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
methods = utils[utils.index('    private static void removeAdsFrom('):utils.index('    // ------------------------------------------------------------- screenshot')]
files = {
'MorpheLog.java': '''package app.browzomje.extension.pinterest;
class MorpheLog {static String ADS="ads",REFLECTION="reflection",SEARCH_MODULES="modules";
static void d(String a,String b){} static void i(String a,String b){} static void w(String a,String b){}
static void w(String a,String b,Throwable t){}static void e(String a,String b,Throwable t){}
static void setStatus(String a,String b){}}''',
'MorpheSettingsStore.java': '''package app.browzomje.extension.pinterest;
class MorpheSettingsStore {static String KEY_HIDE_VIDEO_PINS="video";static boolean video,ads,shopping;
static boolean effective(String k){return video;} static boolean isAdsDisabled(){return ads;}
static boolean isShoppingPinsHidden(){return shopping;}static boolean isSearchBoardModulesHidden(){return false;}}''',
'SearchModuleDetector.java': '''package app.browzomje.extension.pinterest;
class SearchModuleDetector {static boolean isUnwantedSearchModule(Object o){return false;}}''',
'FilterHarness.java': '''package app.browzomje.extension.pinterest;
import java.util.*;
class FilterHarness {
static final int MAX_FILTER_DEPTH=4;static int totalAdsRemoved,totalShoppingPinsRemoved;
static void filterRecursive(Object o,int depth,IdentityHashMap<Object,Boolean> seen){}
static void filter(List<?> list){removeAdsFrom(list,0);}
''' + methods + '\n}',
'VideoTest.java': '''package app.browzomje.extension.pinterest;
import java.lang.annotation.*;import java.util.*;
public class VideoTest {
@Retention(RetentionPolicy.RUNTIME)public @interface Wire {String value();}
static class Pin { @Wire("is_video") private Object renamed; @Wire("is_promoted") boolean ad;
 @Wire("images") Object thumbnail=new Object(); Pin(Object value){renamed=value;} }
static class Inherited extends Pin {Inherited(){super(true);}}
static class Gif {@Wire("is_animated") boolean animated=true; @Wire("videos") Object videos=new Object();}
static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
public static void main(String[] args){
 Pin image=new Pin(false),video=new Pin(true);Gif gif=new Gif();
 check(AdDetector.isVideoPin(video),"video flag");check(AdDetector.isVideoPin(new Inherited()),"inherited private field");
 for(Object o:new Object[]{null,image,gif,new Object(),new Pin(null),new Pin("true"),new Pin(1)})
 check(!AdDetector.isVideoPin(o),"unknown/image/GIF preserved");
 List<Object> list=new ArrayList<>(Arrays.asList(image,video,gif));FilterHarness.filter(list);
 check(list.size()==3,"OFF preserves videos");MorpheSettingsStore.video=true;FilterHarness.filter(list);
 check(list.equals(Arrays.asList(image,gif)),"mixed feed filters only video, preserves order");
 List<Object> second=new ArrayList<>(Arrays.asList(video,image));FilterHarness.filter(second);
 check(second.equals(Arrays.asList(image)),"subsequent page");
 List<Object> all=new ArrayList<>(Arrays.asList(video,new Pin(true)));FilterHarness.filter(all);
 check(all.size()==2,"all filtered page retained for pagination");
 List<Object> immutable=Collections.unmodifiableList(Arrays.asList(video,image));FilterHarness.filter(immutable);
 check(immutable.size()==2,"immutable list safe");
 Pin ad=new Pin(false);ad.ad=true;MorpheSettingsStore.ads=true;
 List<Object> both=new ArrayList<>(Arrays.asList(ad,video,image));FilterHarness.filter(both);
 check(both.equals(Arrays.asList(image)),"ad and video filters compose");
 MorpheSettingsStore.video=false;List<Object> off=new ArrayList<>(Arrays.asList(ad,video,image));FilterHarness.filter(off);
 check(off.equals(Arrays.asList(video,image)),"video OFF independent of ads");
 System.out.println("PASS video classification, OFF/ON, mixed/second/all-video/immutable pages, ad composition");
}}
'''
}
with tempfile.TemporaryDirectory(prefix='pinterest-video-test-') as tmp:
    paths=[]
    for name, body in files.items():
        p=Path(tmp)/name;p.write_text(body);paths.append(str(p))
    subprocess.run(['javac','-d',tmp,*paths,str(src/'AdDetector.java')],check=True)
    subprocess.run(['java','-cp',tmp,'app.browzomje.extension.pinterest.VideoTest'],check=True)
