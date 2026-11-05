#!/usr/bin/env python3
"""Run the production link policy against synthetic, offline fixtures."""
from pathlib import Path
import subprocess
import tempfile

source = Path(__file__).resolve().parents[2] / (
    "extensions/extension/src/main/java/app/template/extension/pinterest/UrlSanitizer.java"
)
stubs = {
    "android/net/Uri.java": """package android.net;
public class Uri {
    public static String decode(String value) {
        return java.net.URLDecoder.decode(value.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
    }
}""",
    "android/text/TextUtils.java": """package android.text;
public class TextUtils {
    public static String join(String delimiter, Iterable<String> values) {
        return String.join(delimiter, values);
    }
}""",
    "android/content/Intent.java": """package android.content;
public class Intent { public static final String EXTRA_TEXT = "android.intent.extra.TEXT"; }""",
    "android/content/ClipData.java": """package android.content;
public class ClipData {
    final Object description; final Item[] items;
    public ClipData(Object description, Item... items) {this.description=description;this.items=items;}
    public int getItemCount(){return items.length;}
    public Item getItemAt(int i){return items[i];}
    public Object getDescription(){return description;}
    public static class Item {
        final String text; final String html;
        public Item(String text){this(text,null);}
        public Item(String text,String html){this.text=text;this.html=html;}
        public String getText(){return text;}
        public String getHtmlText(){return html;}
        public Object getUri(){return null;}
        public Object getIntent(){return null;}
    }
}""",
    "app/browzomje/extension/pinterest/MorpheSettingsStore.java": """package app.browzomje.extension.pinterest;
class MorpheSettingsStore {
    static boolean enabled=true;
    static boolean isLinkSanitizerEnabled(){return enabled;}
    static boolean isShareLinkOnlyEnabled(){return false;}
}""",
    "app/browzomje/extension/pinterest/MorpheLog.java": """package app.browzomje.extension.pinterest;
class MorpheLog {
    static final String SHARE_LINK="share";
    static void hookFired(String tag,String text){}
    static void w(String tag,String text,Throwable error){throw new AssertionError(error);}
}""",
    "app/browzomje/extension/pinterest/LinkTest.java": """package app.browzomje.extension.pinterest;
import android.content.ClipData;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.HttpURLConnection;
class LinkTest {
    static int requests;
    static void mockRedirects() {
        URL.setURLStreamHandlerFactory(protocol -> {
            if(!"https".equals(protocol)) return null;
            return new URLStreamHandler() {
                protected java.net.URLConnection openConnection(URL url) {
                    return new HttpURLConnection(url) {
                        public void connect(){}
                        public void disconnect(){}
                        public boolean usingProxy(){return false;}
                        public int getResponseCode(){
                            check("HEAD".equals(getRequestMethod()) && !getInstanceFollowRedirects(), "controlled HEAD");
                            check("pin.it".equals(url.getHost()), "no external or canonical-host request");
                            requests++;
                            return 302;
                        }
                        public String getHeaderField(String field){
                            if(!"Location".equals(field)) return null;
                            return "/good".equals(url.getPath()) ? "https://www.pinterest.com/pin/123/sent/?sender=sample"
                                    : "https://evil.invalid/pin/123/";
                        }
                    };
                }
            };
        });
    }
    static void check(boolean passed, String description){if(!passed) throw new AssertionError(description);}
    public static void main(String[] args) {
        String pin="https://www.pinterest.com/pin/123/";
        String input=pin+"?a=one+two&UtM_Source=test&a=%2f&flag&sender=sample#anchor";
        String clean=pin+"?a=one+two&a=%2f&flag#anchor";
        check(clean.equals(UrlSanitizer.stripTrackingParams(input)), "preserve query bytes, order and fragment");
        for(String key:new String[]{"%73ignature","token","code","X-Amz-Signature","state"}) {
            String protectedUrl=pin+"?utm_source=x&"+key+"=sample";
            check(protectedUrl.equals(UrlSanitizer.sanitizeText(protectedUrl)), "protected query "+key);
        }
        String regional="https://www.pinterest.co.uk/pin/123/";
        check(regional.equals(UrlSanitizer.sanitizeText(regional+"?utm_source=sample")), "regional direct link cleaned");
        mockRedirects();
        String shortLink="https://pin.it/good";
        check("https://pinterest.com/pin/123/".equals(UrlSanitizer.sanitizeText(shortLink)), "short link resolves");
        check("https://pinterest.com/pin/123/".equals(UrlSanitizer.sanitizeText(shortLink)) && requests==1,
                "successful resolution is cached");
        String unsafeShort="https://pin.it/bad";
        String unsafeText=unsafeShort+" "+unsafeShort;
        check(unsafeText.equals(UrlSanitizer.sanitizeText(unsafeText)) && requests==2,
                "external hop is never fetched and failed duplicate resolves once");
        String signedShort="https://pin.it/test?token=sample&utm_source=x";
        check(signedShort.equals(UrlSanitizer.sanitizeText(signedShort)) && requests==2,
                "protected short link stays untouched without a request");
        for(String host:new String[]{"pinterest.com.evil.invalid","evil-pinterest.com","pinterest.com@evil.invalid",
                "www.pinterest.com:8443"}) {
            String lookalike="https://"+host+"/pin/123/?utm_source=x";
            check(lookalike.equals(UrlSanitizer.sanitizeText(lookalike)), "untrusted host "+host);
        }
        URI base=URI.create("https://api.pinterest.com/url_shortener/short/redirect/");
        check(URI.create("https://api.pinterest.com/pin/123/").equals(
                UrlSanitizer.trustedRedirect(base, "/pin/123/")), "relative redirect");
        for(String destination:new String[]{"https://evil.invalid/pin/123/","http://pinterest.com/pin/123/",
                "//pinterest.com.evil.invalid/pin/123/","https://pinterest.com/pin/123/?code=sample",
                "https://user@pinterest.com/pin/123/","https://pinterest.com:8443/pin/123/","https://[invalid"}) {
            check(UrlSanitizer.trustedRedirect(base,destination)==null,"reject redirect "+destination);
        }
        String plain="prefix "+input+" suffix "+pin+"?utm_medium=sample";
        check(("prefix "+clean+" suffix "+pin).equals(UrlSanitizer.sanitizeText(plain)), "two URLs, surrounding text");
        Object description=new Object();
        ClipData original=new ClipData(description,new ClipData.Item(input));
        ClipData copied=UrlSanitizer.sanitizeClipData(original);
        check(copied.getDescription()==description && clean.equals(copied.getItemAt(0).getText()), "plain clipboard");
        ClipData rich=new ClipData(description,new ClipData.Item(input,"<b>example</b>"));
        ClipData multiple=new ClipData(description,new ClipData.Item(input),new ClipData.Item("other"));
        check(UrlSanitizer.sanitizeClipData(rich)==rich && UrlSanitizer.sanitizeClipData(multiple)==multiple,
                "rich or multiple clipboard items preserved");
        check(clean.equals(UrlSanitizer.sanitizeExtra(android.content.Intent.EXTRA_TEXT,input)),
                "share text uses the same policy");
        check(input.equals(UrlSanitizer.sanitizeExtra("unrelated-extra",input)), "unrelated extra preserved");
        MorpheSettingsStore.enabled=false;
        check(input.equals(UrlSanitizer.sanitizeExtra(android.content.Intent.EXTRA_TEXT,input)), "disabled share path preserved");
        check(UrlSanitizer.sanitizeClipData(original)==original, "disabled clip path preserved");
        System.out.println("PASS link tracking, protected URLs, redirect boundaries and clipboard preservation");
    }
}""",
}
with tempfile.TemporaryDirectory(prefix="pinterest-link-test-") as work:
    for name, contents in stubs.items():
        path = Path(work, name)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)
    subprocess.run(["javac", "-d", work, str(source), *(str(Path(work, name)) for name in stubs)], check=True)
    subprocess.run(["java", "-cp", work, "app.browzomje.extension.pinterest.LinkTest"], check=True)
