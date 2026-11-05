#!/usr/bin/env python3
"""JVM check of production detector and filtering methods with synthetic API models.

Only the feed filtering methods are extracted from PinterestUtils to avoid stubbing
unrelated Android UI/download code. No filtering logic is copied into the harness.
"""
from pathlib import Path
import subprocess
import tempfile

src = Path(__file__).resolve().parents[2] / 'extensions/extension/src/main/java/app/template/extension/pinterest'
utils = (src / 'PinterestUtils.java').read_text()
consent = utils[utils.index('    public static boolean denyMetaAdsConsent('):utils.index('    /** @return true se almeno')]
methods = utils[utils.index('    private static void removeAdsFrom('):utils.index('    // ------------------------------------------------------------- screenshot')]
files = {
'MorpheLog.java': '''package app.browzomje.extension.pinterest;
class MorpheLog {static String ADS="ads",REFLECTION="reflection",SEARCH_MODULES="modules";
static void d(String a,String b){} static void i(String a,String b){} static void w(String a,String b){}
static void w(String a,String b,Throwable t){}static void e(String a,String b,Throwable t){}
static void setStatus(String a,String b){}}''',
'MorpheSettingsStore.java': '''package app.browzomje.extension.pinterest;
class MorpheSettingsStore {static String KEY_HIDE_VIDEO_PINS="video",KEY_HIDE_AI_PINS="ai";static boolean video,ai,ads,shopping;static String words="";
static String mutedPinWords(){return words;}
static boolean effective(String k){return k.equals(KEY_HIDE_VIDEO_PINS)?video:ai;} static boolean isAdsDisabled(){return ads;}
static boolean isShoppingPinsHidden(){return shopping;}static boolean isSearchBoardModulesHidden(){return false;}}''',
'SearchModuleDetector.java': '''package app.browzomje.extension.pinterest;
class SearchModuleDetector {static boolean isUnwantedSearchModule(Object o){return false;}}''',
'FilterHarness.java': '''package app.browzomje.extension.pinterest;
import java.util.*;
class FilterHarness {
static final int MAX_FILTER_DEPTH=4;static int totalAdsRemoved,totalShoppingPinsRemoved;
static void filterRecursive(Object o,int depth,IdentityHashMap<Object,Boolean> seen){}
static void filter(List<?> list){removeAdsFrom(list,0);}
''' + consent + methods + '\n}',
'VideoTest.java': '''package app.browzomje.extension.pinterest;
import java.lang.annotation.*;import java.util.*;
public class VideoTest {
@Retention(RetentionPolicy.RUNTIME)public @interface Wire {String value();}
static class Pin { @Wire("is_video") private Object renamed; @Wire("is_promoted") boolean ad;
 @Wire("images") Object thumbnail=new Object(); Pin(Object value){renamed=value;} }
enum Vendor { META_AUDIENCE_NETWORK, GOOGLE_MOBILE_ADS }
enum SourceType { DETECTED_TRAINED_ALGORITHMIC_MEDIA, UNKNOWN }
static class Inherited extends Pin {Inherited(){super(true);}}
static class Gif {@Wire("is_animated") boolean animated=true; @Wire("videos") Object videos=new Object();}
static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
public static void main(String[] args){
 Pin image=new Pin(false),video=new Pin(true);Gif gif=new Gif();
 check(!FilterHarness.denyMetaAdsConsent(Vendor.META_AUDIENCE_NETWORK),"ads off uses native Meta consent");
 MorpheSettingsStore.ads=true;
 check(FilterHarness.denyMetaAdsConsent(Vendor.META_AUDIENCE_NETWORK),"ads filter blocks Meta consent");
 check(!FilterHarness.denyMetaAdsConsent(Vendor.GOOGLE_MOBILE_ADS)
       && !FilterHarness.denyMetaAdsConsent("META_AUDIENCE_NETWORK"),"other vendors and non-enums use native consent");
 MorpheSettingsStore.ads=false;
 check(AdDetector.isVideoPin(video),"video flag");check(AdDetector.isVideoPin(new Inherited()),"inherited private field");
 for(Object o:new Object[]{null,image,gif,new Object(),new Pin(null),new Pin("true"),new Pin(1)})
 check(!AdDetector.isVideoPin(o),"unknown/image/GIF preserved");
 check(AdDetector.isVideoPin(Map.of("videos",Map.of("video_list",Map.of("V_HLSV4",new Object())))),"video tracks without flag");
 Object story=Map.of("story_pin_data",Map.of("pages",List.of(Map.of("video",Map.of("video_list",Map.of("V_720P",new Object()))))));
 check(AdDetector.isVideoPin(story),"story video without flag");
 check(AdDetector.isVideoPin(Map.of("story_pin_data",Map.of("total_video_duration","13.0"))),"search summary without pages/tracks");
 check(AdDetector.isVideoPin(Map.of("story_pin_data",Map.of("total_video_duration",0.01))),"numeric duration");
 for(Object duration:new Object[]{"0","0.0","-1","NaN","Infinity","unknown","",false})
 check(!AdDetector.isVideoPin(Map.of("story_pin_data",Map.of("total_video_duration",duration))),"invalid/zero duration preserved");
 check(!AdDetector.isVideoPin(Map.of("videos",Map.of("video_list",Map.of()))),"empty video metadata preserved");
 List<Object> list=new ArrayList<>(Arrays.asList(image,video,gif));FilterHarness.filter(list);
 check(list.size()==3,"OFF preserves videos");MorpheSettingsStore.video=true;FilterHarness.filter(list);
 check(list.equals(Arrays.asList(image,gif)),"mixed feed filters only video, preserves order");
 List<Object> second=new ArrayList<>(Arrays.asList(video,image));FilterHarness.filter(second);
 check(second.equals(Arrays.asList(image)),"subsequent page");
 List<Object> all=new ArrayList<>(Arrays.asList(video,new Pin(true)));FilterHarness.filter(all);
 check(all.isEmpty(),"all-video page is actually filtered");
 List<Object> immutable=Collections.unmodifiableList(Arrays.asList(video,image));FilterHarness.filter(immutable);
 check(immutable.size()==2,"immutable list safe");
 Pin ad=new Pin(false);ad.ad=true;MorpheSettingsStore.ads=true;
 List<Object> both=new ArrayList<>(Arrays.asList(ad,video,image));FilterHarness.filter(both);
 check(both.equals(Arrays.asList(image)),"ad and video filters compose");
 MorpheSettingsStore.video=false;List<Object> off=new ArrayList<>(Arrays.asList(ad,video,image));FilterHarness.filter(off);
 check(off.equals(Arrays.asList(video,image)),"video OFF independent of ads");
 MorpheSettingsStore.video=true;
 List<Object> cached=Collections.unmodifiableList(Arrays.asList(image,video,gif));
 List<?> filtered=VideoFeedFilter.filter(cached);
 check(filtered.equals(Arrays.asList(image,gif))&&cached.size()==3,"legacy/search copy without mutating adapter list");
 check(VideoFeedFilter.filter(List.of(video)).isEmpty(),"legacy all-video list empty");
 check(VideoFeedFilter.filter(List.of(image)).equals(List.of(image)),"legacy image retained");
 MorpheSettingsStore.video=false;check(VideoFeedFilter.filter(cached)==cached,"legacy OFF returns original list");
 Object labeled=Map.of("digital_media_source_type",SourceType.DETECTED_TRAINED_ALGORITHMIC_MEDIA);
 Object legacy=Map.of("ai_disclosures",List.of(2));
 Object unknown=Map.of("digital_media_source_type",SourceType.UNKNOWN,"ai_disclosures",List.of(0),"digital_media_source_type_label"," ");
 Object label=Map.of("digital_media_source_type_label","AI modified");
 List<Object> aiItems=List.of(labeled,legacy,unknown,label,image);
 check(VideoFeedFilter.filter(aiItems)==aiItems,"AI opt-in preserves the page by default");
 MorpheSettingsStore.ai=true;
 check(VideoFeedFilter.filter(aiItems).equals(List.of(unknown,image)),"AI grid and search honor disclosed labels only");
 List<Object> aiFeed=new ArrayList<>(aiItems);FilterHarness.filter(aiFeed);
 check(aiFeed.equals(List.of(unknown,image)),"AI feed path agrees with grid filter");
 MorpheSettingsStore.words="café\\niced tea";
 Object accent=Map.of("images",Map.of(),"title","A CAFE\\u0301 recipe");
 Object phrase=Map.of("grid_title","Iced tea.");
 Object substring=Map.of("grid_title","Caféine and spiced tea");
 Object noTitle=Map.of("images",Map.of());
 List<Object> words=List.of(accent,phrase,substring,noTitle);
 check(VideoFeedFilter.filter(words).equals(List.of(substring,noTitle)),"muted words normalize accents and match whole phrases");
 List<Object> mutableWords=new ArrayList<>(words);FilterHarness.filter(mutableWords);
 check(mutableWords.equals(List.of(substring,noTitle)),"muted words apply to existing feed lists");
 MorpheSettingsStore.words="";
 check(VideoFeedFilter.filter(words)==words,"empty muted words retain original list");
 System.out.println("PASS video and AI classification, OFF/ON, mixed/second/all-video/immutable pages, ad composition");
}}
'''
}
with tempfile.TemporaryDirectory(prefix='pinterest-video-test-') as tmp:
    paths=[]
    for name, body in files.items():
        p=Path(tmp)/name;p.write_text(body);paths.append(str(p))
    subprocess.run(['javac','-d',tmp,*paths,str(src/'AdDetector.java'),str(src/'VideoFeedFilter.java'),str(src/'MutedPinWords.java')],check=True)
    subprocess.run(['java','-cp',tmp,'app.browzomje.extension.pinterest.VideoTest'],check=True)
