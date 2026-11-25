package app.browzomje.extension.pinterest;

import android.content.Context;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

public final class ComposePinDownload {
    private ComposePinDownload() {}

    public static void append(ArrayList<Object> options) {
        Context context = MorpheSettingsStore.appContext();
        if (context == null || !MorpheSettingsStore.isLongPressDownloadEnabled()) return;
        try {
            Class<?> type = Class.forName("ia3.z");
            Class<?> icons = Class.forName("d52.o0");
            Object downloadIcon = icons.getField("DOWNLOAD").get(null);
            int label = context.getResources().getIdentifier("download", "string", context.getPackageName());
            if (label == 0) return;
            Class<?> pair = Class.forName("kotlin.Pair");
            Object presentation = pair.getConstructor(Object.class, Object.class).newInstance(downloadIcon, label);
            options.add(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    new DownloadOption(presentation)));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            MorpheLog.w(MorpheLog.BOARD, "could not add the list-style long-press download", failure);
        }
    }

    public static boolean download(Object option, Object selectedPin) {
        if (option == null || !Proxy.isProxyClass(option.getClass())
                || !(Proxy.getInvocationHandler(option) instanceof DownloadOption)) return false;
        Context context = MorpheSettingsStore.appContext();
        if (context != null && selectedPin != null && MorpheSettingsStore.isLongPressDownloadEnabled()) {
            BoardDownloadHandler.downloadSinglePin(context, selectedPin);
        }
        return true;
    }

    private static final class DownloadOption implements InvocationHandler {
        private final Object presentation;

        DownloadOption(Object presentation) {
            this.presentation = presentation;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "iconAndLabel": return presentation;
                case "getAnalyticsName":
                case "toString": return "morphe_download";
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                default: throw new UnsupportedOperationException(method.getName());
            }
        }
    }
}
