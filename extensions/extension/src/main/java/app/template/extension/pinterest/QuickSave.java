package app.browzomje.extension.pinterest;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Pinterest 14.44.0 close-up and radial profile save actions. */
public final class QuickSave {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<View, Binding> BINDINGS = new WeakHashMap<>();
    private static final Map<View, Object> MODULE_PINS = new WeakHashMap<>();
    private static final Map<String, Membership> MEMBERSHIPS = new LinkedHashMap<>(32, .75f, true);
    private static final Map<String, PendingRead> READS = new HashMap<>();
    private static final Map<String, PendingWrite> WRITES = new HashMap<>();
    private static final long CACHE_MS = 60_000;

    private static final class Membership {
        final boolean profile;
        final boolean boards;
        final long checked;

        Membership(boolean profile, boolean boards, long checked) {
            this.profile = profile;
            this.boards = boards;
            this.checked = checked;
        }
    }

    private static final class Binding {
        final String pin;
        final Object model;
        final boolean radial;
        final int pinLabel;
        final int pinIcon;

        Binding(String pin, Object model, boolean radial, int pinLabel, int pinIcon) {
            this.pin = pin;
            this.model = model;
            this.radial = radial;
            this.pinLabel = pinLabel;
            this.pinIcon = pinIcon;
        }
    }

    private static final class Tap {
        final View view;
        final Binding binding;

        Tap(View view, Binding binding) {
            this.view = view;
            this.binding = binding;
        }
    }
    private static final class PendingRead {
        Tap tap;
    }

    private static final class PendingWrite {
        final Membership confirmed;
        final boolean sent;
        boolean desired;

        PendingWrite(Membership confirmed, boolean desired) {
            this.confirmed = confirmed;
            this.sent = desired;
            this.desired = desired;
        }

        Membership visible() {
            return new Membership(desired, confirmed.boards, confirmed.checked);
        }

        boolean needsFollowUp() {
            return desired != sent;
        }
    }

    private QuickSave() {}

    public static void bindButton(Object binder, int ignoredCount, Object model) {
        try {
            View button = (View) field(binder, "b");
            boolean split = button.getVisibility() != View.VISIBLE;
            if (split) {
                BINDINGS.remove(button);
                Object group = field(binder, "f");
                if (!(group instanceof View) || ((View) group).getVisibility() != View.VISIBLE) return;
                button = (View) call(group, "w1");
            }
            bind(button, model, false, split, 0, 0);
            button.setOnLongClickListener(view -> openBoardPicker(view, model));
        } catch (ReflectiveOperationException | RuntimeException error) {
            MorpheLog.w(MorpheLog.BOARD, "could not bind quick save button", error);
        }
    }

    public static void bindModule(Object module, Object model) {
        if (!(module instanceof View)) return;
        View root = (View) module;
        MODULE_PINS.put(root, model);
        root.post(() -> {
            if (MODULE_PINS.get(root) != model) return;
            View button = root.findViewById(resource(root, "save_pinit_bt", "id"));
            if (button == null || !button.isShown() || !button.isEnabled()) return;
            try {
                bind(button, model, false, false, 0, 0);
                button.setOnLongClickListener(view -> openBoardPicker(view, model));
            } catch (ReflectiveOperationException | RuntimeException error) {
                MorpheLog.w(MorpheLog.BOARD, "could not bind legacy quick save button", error);
            }
        });
    }

    public static void bindRadial(Object menu, Object selection) {
        try {
            Object model = call(selection, "a");
            if (model == null || !model.getClass().getName().equals("com.pinterest.api.model.pe")) return;
            for (Object candidate : (List<?>) field(menu, "e")) {
                View item = (View) candidate;
                int save = resource(item, "context_save", "id");
                if (save == 0 || !Integer.valueOf(save).equals(field(item, "j"))) continue;
                bind(item, model, true, true, resource(item, "contextmenu_pinit", "string"),
                        resource(item, "ic_vr_pin_angled_gestalt", "drawable"));
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            MorpheLog.w(MorpheLog.BOARD, "could not bind radial quick save", error);
        }
    }

    private static void bind(View view, Object model, boolean radial, boolean plainClick, int pinLabel, int pinIcon)
            throws ReflectiveOperationException {
        String pin = (String) AdDetector.fieldValueBySerializedName(model, "id");
        if (pin == null || !pin.matches("[0-9]+")) {
            BINDINGS.remove(view);
            if (!radial) view.setOnLongClickListener(null);
            return;
        }
        Object account = account();
        if (account == null) return;
        String key = key(account, pin);
        Binding binding = new Binding(pin, model, radial, pinLabel, pinIcon);
        Membership known = MEMBERSHIPS.get(key);
        if (!isFresh(known)) {
            refresh(key, pin);
        }

        BINDINGS.put(view, binding);
        View.OnClickListener click = target -> toggle(target, binding);
        if (plainClick) view.setOnClickListener(click);
        else view.getClass().getMethod("c", View.OnClickListener.class).invoke(view, click);
        PendingWrite pending = WRITES.get(key);
        Membership visible = pending == null ? known : pending.visible();
        if (visible != null) render(view, binding, visible);
    }

    private static void refresh(String key, String pin) throws ReflectiveOperationException {
        if (READS.containsKey(key) || WRITES.containsKey(key)) return;
        PendingRead read = new PendingRead();
        READS.put(key, read);
        try {
            request("getPinBoards", new Object[]{pin, true}, (data) -> {
                READS.remove(key);
                Tap tap = read.tap;
                try {
                    if (!sameAccount(key, pin)) return;
                    if (data == null) {
                        if (tap != null) failure(tap.view);
                        return;
                    }
                    Membership known = membership(data);
                    remember(key, known);
                    if (tap != null) change(tap.view, tap.binding, key, known);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    if (tap != null) failure(tap.view);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            READS.remove(key);
            throw error;
        }
    }

    private static void toggle(View view, Binding binding) {
        if (BINDINGS.get(view) != binding) return;
        try {
            Object account = account();
            if (account == null) return;
            String key = key(account, binding.pin);
            PendingWrite pending = WRITES.get(key);
            if (pending != null) {
                pending.desired = !pending.desired;
                renderBound(key, pending.visible());
                return;
            }
            Membership known = MEMBERSHIPS.get(key);
            if (isFresh(known) && !READS.containsKey(key)) {
                change(view, binding, key, known);
                return;
            }
            refresh(key, binding.pin);
            PendingRead read = READS.get(key);
            read.tap = read.tap == null ? new Tap(view, binding) : null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            failure(view);
        }
    }

    private static void change(View view, Binding binding, String key, Membership known)
            throws ReflectiveOperationException {
        if (!known.profile && known.boards) {
            openBoardPicker(view, binding.model);
            return;
        }
        write(view, binding.pin, key, known, !known.profile);
    }

    private static void write(View view, String pin, String key, Membership known, boolean desired)
            throws ReflectiveOperationException {
        PendingWrite pending = new PendingWrite(known, desired);
        WRITES.put(key, pending);
        WeakReference<View> anchor = new WeakReference<>(view);
        try {
            renderBound(key, pending.visible());
            request("reconcilePinBoards", new Object[]{pin, null, null, desired, null},
                    data -> finishWrite(anchor.get(), pin, key, pending, data));
        } catch (ReflectiveOperationException | RuntimeException error) {
            WRITES.remove(key);
            MEMBERSHIPS.remove(key);
            renderBound(key, known);
            throw error;
        }
    }

    private static void finishWrite(View view, String pin, String key, PendingWrite pending, Object data) {
        WRITES.remove(key);
        try {
            if (!sameAccount(key, pin)) return;
            boolean succeeded = data != null && "success".equals(call(data, "h"))
                    && Boolean.valueOf(pending.sent).equals(call(data, "f"));
            Membership confirmed = pending.confirmed;
            if (succeeded) {
                confirmed = new Membership(pending.sent, confirmed.boards, android.os.SystemClock.elapsedRealtime());
            }
            if (pending.needsFollowUp()) {
                // The earlier reply may be ambiguous. Send the latest explicit state even
                // if it equals the last confirmed value.
                write(view, pin, key, confirmed, pending.desired);
            } else if (succeeded) {
                remember(key, confirmed);
            } else {
                MEMBERSHIPS.remove(key);
                renderBound(key, confirmed);
                if (view != null) failure(view);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            MEMBERSHIPS.remove(key);
            if (view != null) failure(view);
        }
    }

    private static boolean isFresh(Membership known) {
        return known != null && android.os.SystemClock.elapsedRealtime() - known.checked <= CACHE_MS;
    }

    private static Membership membership(Object data) throws ReflectiveOperationException {
        boolean profile = Boolean.TRUE.equals(call(data, "d"));
        List<?> boards = (List<?>) call(data, "c");
        List<?> sections = (List<?>) call(data, "e");
        return new Membership(profile, !boards.isEmpty() || !sections.isEmpty(),
                android.os.SystemClock.elapsedRealtime());
    }

    private static void remember(String key, Membership membership) throws ReflectiveOperationException {
        MEMBERSHIPS.put(key, membership);
        if (MEMBERSHIPS.size() > 256) MEMBERSHIPS.remove(MEMBERSHIPS.keySet().iterator().next());
        renderBound(key, membership);
    }

    private static void renderBound(String key, Membership membership) throws ReflectiveOperationException {
        Object account = account();
        if (account == null) return;
        for (var entry : new WeakHashMap<>(BINDINGS).entrySet()) {
            if (entry.getKey() != null && key.equals(key(account, entry.getValue().pin))) {
                render(entry.getKey(), entry.getValue(), membership);
            }
        }
    }

    private static void render(View view, Binding binding, Membership known) {
        try {
            if (binding.radial) {
                int label = known.profile ? resource(view, "one_tap_unsave_pin", "string") : binding.pinLabel;
                int icon = known.profile ? resource(view, "ic_vr_pin_slash_gestalt", "drawable") : binding.pinIcon;
                if (label != 0 && icon != 0) {
                    view.getClass().getMethod("h", int.class).invoke(view, label);
                    view.getClass().getMethod("f", int.class).invoke(view, icon);
                    view.setContentDescription(view.getResources().getText(label));
                }
            } else {
                String name = known.profile ? "remove" : known.boards ? "saved" : "save";
                int label = resource(view, name, "string");
                TextView text = PinterestUtils.findTextView(view);
                if (label != 0 && text != null) {
                    text.setText(label);
                    view.setContentDescription(view.getResources().getText(label));
                }
                view.setSelected(known.profile || known.boards);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            MorpheLog.w(MorpheLog.BOARD, "could not update quick save appearance", error);
        }
    }

    private interface Response { void received(Object data); }

    private static void request(String operation, Object[] args, Response response)
            throws ReflectiveOperationException {
        Class<?> continuation = Class.forName("r83.a");
        Object empty = Class.forName("kotlin.coroutines.g").getField("a").get(null);
        Object service = service();
        Class<?> api = Class.forName("com.pinterest.repository.pin.PinService");
        Method method = operation.equals("getPinBoards")
                ? api.getMethod(operation, String.class, boolean.class, continuation)
                : api.getMethod(operation, String.class, String.class, String.class, Boolean.class,
                        String.class, continuation);
        Object[] parameters = java.util.Arrays.copyOf(args, args.length + 1);
        parameters[args.length] = Proxy.newProxyInstance(continuation.getClassLoader(),
                new Class<?>[]{continuation}, (proxy, invoked, values) -> {
                    switch (invoked.getName()) {
                        case "getContext": return empty;
                        case "resumeWith": deliver(values[0], response); return null;
                        case "toString": return "Morphe profile save continuation";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == values[0];
                        default: return null;
                    }
                });
        Object result = method.invoke(service, parameters);
        Object suspended = Class.forName("s83.a").getField("COROUTINE_SUSPENDED").get(null);
        if (result != suspended) deliver(result, response);
    }

    private static void deliver(Object result, Response response) {
        MAIN.post(() -> {
            Object data = null;
            try {
                if (result != null && result.getClass().getName().equals("y60.b")) {
                    data = call(field(result, "a"), "c");
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                MorpheLog.w(MorpheLog.BOARD, "could not read quick save response", error);
            }
            MorpheLog.d(MorpheLog.BOARD, "quick save response " + (data != null ? "received" : "unavailable"));
            response.received(data);
        });
    }

    private static Object service() throws ReflectiveOperationException {
        Object application = Class.forName("com.bumptech.glide.c").getMethod("o").invoke(null);
        Object component = Class.forName("w72.u").getMethod("s", Context.class, Class.class)
                .invoke(null, application, Class.forName("qd0.c"));
        Object provider = field(component, "h4");
        return Class.forName("m83.a").getMethod("get").invoke(provider);
    }

    private static boolean openBoardPicker(View view, Object model) {
        try {
            Activity activity = PinterestUtils.activityOf(view.getContext());
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
            Object component = Class.forName("yb.a0").getMethod("B", Class.class, Object.class)
                    .invoke(null, Class.forName("je1.b"), activity);
            Object actions = call(component, "n7");
            actions.getClass().getMethod("f", model.getClass(), String.class, String.class, HashMap.class)
                    .invoke(actions, model, null, null, new HashMap<>());
            Object account = account();
            Object pin = AdDetector.fieldValueBySerializedName(model, "id");
            if (account != null && pin instanceof String) MEMBERSHIPS.remove(key(account, (String) pin));
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            MorpheLog.w(MorpheLog.BOARD, "could not open the native board picker", error);
            failure(view);
            return true;
        }
    }

    private static Object account() throws ReflectiveOperationException {
        Object session = Class.forName("yb.a0").getMethod("C").invoke(null);
        return call(session, "d");
    }

    private static boolean sameAccount(String key, String pin) throws ReflectiveOperationException {
        Object account = account();
        return account != null && key.equals(key(account, pin));
    }

    private static String key(Object account, String pin) {
        return AdDetector.fieldValueBySerializedName(account, "id") + ":" + pin;
    }

    private static Object field(Object value, String name) throws ReflectiveOperationException {
        return value.getClass().getField(name).get(value);
    }

    private static Object call(Object value, String name) throws ReflectiveOperationException {
        return value.getClass().getMethod(name).invoke(value);
    }

    private static int resource(View view, String name, String type) {
        return view.getResources().getIdentifier(name, type, view.getContext().getPackageName());
    }

    private static void failure(View view) {
        toast(view, "oops_something_went_wrong");
    }

    private static void toast(View view, String name) {
        int id = resource(view, name, "string");
        if (id != 0) Toast.makeText(view.getContext(), id, Toast.LENGTH_SHORT).show();
    }
}
