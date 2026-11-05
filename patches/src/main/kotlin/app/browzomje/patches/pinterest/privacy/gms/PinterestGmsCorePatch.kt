package app.browzomje.patches.pinterest.privacy.gms

import app.browzomje.patches.shared.Constants.COMPATIBILITY_PINTEREST
import app.browzomje.patches.shared.PatchLog
import app.browzomje.patches.shared.forceReturn
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.w3c.dom.Element

private const val PATCH_NAME = "GmsCore support for Pinterest"
private const val STOCK_PACKAGE = "com.google.android.gms"
private const val MICROG_PACKAGE = "app.revanced.android.gms"
private const val ORIGINAL_SIGNER_SHA1 = "b6a74dbcb894b0f73d8c485c72eb1247a8f027ca"
private const val MESSAGING_RECEIVE = "com.google.android.c2dm.intent.RECEIVE"
private const val MESSAGING_REGISTER = "com.google.android.c2dm.intent.REGISTER"
private const val TOKEN_REQUEST = "com.google.iid.TOKEN_REQUEST"
private const val GOOGLE_SIGN_IN = "com.google.android.gms.auth.api.identity.service.signin.START"
private const val IDENTITY_CREDENTIALS = "com.google.android.gms.identitycredentials.service.START"

// Endpoint names shared by Pinterest 14.43.0 and the supported microG-RE provider.
private val routedNames = setOf(
    "com.google", STOCK_PACKAGE, MESSAGING_RECEIVE, MESSAGING_REGISTER, TOKEN_REQUEST,
    "com.google.android.c2dm.permission.SEND", "com.google.android.c2dm.permission.RECEIVE",
    GOOGLE_SIGN_IN, IDENTITY_CREDENTIALS,
    "com.google.android.gms.auth.GOOGLE_SIGN_IN",
    "com.google.android.gms.auth.api.credentials.service.START",
    "com.google.android.gms.auth.api.identity.service.credentialsaving.START",
    "com.google.android.gms.auth.api.signin.service.START",
    "com.google.android.gms.auth.api.phone.service.SmsRetrieverApiService.START",
    "com.google.android.gms.auth.api.phone.permission.SEND",
    "com.google.android.gms.auth.blockstore.service.START",
    "com.google.android.gms.signin.service.START",
    "com.google.android.gms.ads.service.START",
    "com.google.android.gms.appset.service.START",
    "com.google.android.gms.common.telemetry.service.START",
    "com.google.android.gms.fido.fido2.regular.START",
    "com.google.android.gms.gass.START",
    "com.google.android.gms.measurement.START",
    "com.google.android.location.internal.GoogleLocationManagerService.START",
    "com.google.android.providers.gsf.permission.READ_GSERVICES",
)

private val routedAuthorities = setOf(
    "com.google.android.gms.chimera", "com.google.android.gms.fonts", "com.google.android.gms.phenotype",
)

private fun microGName(value: String): String? {
    if (value in routedNames || value in routedAuthorities) {
        return "app.revanced" + value.removePrefix("com.google")
    }
    if (!value.startsWith("content://")) return null
    val authority = value.removePrefix("content://").substringBefore('/')
    if (authority !in routedAuthorities) return null
    return "content://app.revanced" + value.removePrefix("content://com.google")
}

private val microGManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            val nodes = root.getElementsByTagName("*")
            val elements = (0 until nodes.length).map { nodes.item(it) as Element }
            val routes = listOf(
                Triple("uses-permission", "name", "com.google.android.c2dm.permission.RECEIVE"),
                Triple("receiver", "permission", "com.google.android.c2dm.permission.SEND"),
                Triple("action", "name", MESSAGING_RECEIVE),
            ).map { (tag, attribute, value) ->
                val matches = elements.filter { it.tagName == tag && it.getAttribute("android:$attribute") == value }
                check(matches.size == 1) { "Expected one $tag with $value; found ${matches.size}" }
                val element = matches.single()
                Triple(element, attribute, checkNotNull(microGName(value)))
            }
            val app = elements.single { it.tagName == "application" }
            val metadataName = "$MICROG_PACKAGE.SPOOFED_PACKAGE_SIGNATURE"
            check(elements.none { it.getAttribute("android:name") == metadataName })
            for ((element, attribute, value) in routes) {
                element.setAttribute("android:$attribute", value)
            }
            val queries = elements.firstOrNull { it.tagName == "queries" }
                ?: manifest.createElement("queries").also { root.insertBefore(it, app) }
            queries.appendChild(manifest.createElement("package").apply {
                setAttribute("android:name", MICROG_PACKAGE)
            })
            app.appendChild(manifest.createElement("meta-data").apply {
                setAttribute("android:name", metadataName)
                setAttribute("android:value", ORIGINAL_SIGNER_SHA1)
            })
        }
    }
}

private data class Route(val method: Method, val index: Int, val value: String)
private data class FieldRoute(val owner: String, val name: String, val value: String)

@Suppress("unused")
val pinterestGmsCorePatch = bytecodePatch(
    name = PATCH_NAME,
    description = "Routes Google sign-in and push registration to microG-RE. Select only with a configured microG-RE installation.",
    default = false,
) {
    compatibleWith(Compatibility(
        name = "Pinterest",
        packageName = "com.pinterest",
        apkFileType = COMPATIBILITY_PINTEREST.apkFileType,
        targets = COMPATIBILITY_PINTEREST.targets!!.filter { it.version == "14.43.0" },
    ))
    dependsOn(microGManifestPatch)
    extendWith("extensions/extension.mpe")

    execute {
        val routes = mutableListOf<Route>()
        val fields = mutableListOf<FieldRoute>()
        val availability = mutableListOf<Method>()
        val frameworkProviders = mutableListOf<Method>()
        val rewrittenNames = mutableSetOf<String>()
        classDefForEach { owner ->
            if (owner.type.startsWith("Lapp/browzomje/extension/")) return@classDefForEach
            for (field in owner.staticFields) {
                val value = (field.initialValue as? StringEncodedValue)?.value ?: continue
                val target = microGName(value) ?: continue
                fields += FieldRoute(owner.type, field.name, target)
                rewrittenNames += value
            }
            val framework = owner.fields.any { it.type == "Landroid/credentials/CredentialManager;" }
            for (method in owner.methods) {
                val instructions = method.implementation?.instructions ?: continue
                val availabilityShape = method.returnType == "I" &&
                    method.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;", "I")
                val anchors = if (availabilityShape) mutableSetOf<String>() else null
                instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.CONST_STRING && instruction.opcode != Opcode.CONST_STRING_JUMBO) {
                        return@forEachIndexed
                    }
                    val value = ((instruction as ReferenceInstruction).reference as StringReference).string
                    anchors?.add(value)
                    val target = microGName(value) ?: return@forEachIndexed
                    routes += Route(method, index, target)
                    rewrittenNames += value
                }
                if (anchors?.containsAll(listOf(STOCK_PACKAGE, "MetadataValueReader", "This should never happen.")) == true) {
                    availability += method
                }
                if (framework && method.name == "isAvailableOnDevice" && method.returnType == "Z" &&
                    method.parameterTypes.isEmpty()) {
                    frameworkProviders += method
                }
            }
        }
        val required = setOf(STOCK_PACKAGE, "com.google", GOOGLE_SIGN_IN, IDENTITY_CREDENTIALS,
            MESSAGING_RECEIVE, MESSAGING_REGISTER, TOKEN_REQUEST)
        check(rewrittenNames.containsAll(required)) { "Missing microG routes: ${required - rewrittenNames}" }
        val availabilityMethod = availability.single()
        val frameworkMethod = frameworkProviders.single()

        for ((method, edits) in routes.groupBy(Route::method)) {
            val target = mutableClassDefBy(method.definingClass).methods.single {
                it.name == method.name && it.returnType == method.returnType && it.parameterTypes == method.parameterTypes
            }
            for (edit in edits) {
                val instruction = target.implementation!!.instructions[edit.index]
                val register = (instruction as OneRegisterInstruction).registerA
                val reference = ImmutableStringReference(edit.value)
                target.replaceInstruction(edit.index, if (instruction.opcode == Opcode.CONST_STRING) {
                    BuilderInstruction21c(Opcode.CONST_STRING, register, reference)
                } else {
                    BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, register, reference)
                })
            }
        }
        for (field in fields) {
            val target = mutableClassDefBy(field.owner).staticFields.single { it.name == field.name }
            (target.initialValue as MutableStringEncodedValue).value = field.value
        }

        // Use the redirected provider instead of Android 14's stock framework provider.
        for (method in listOf(availabilityMethod, frameworkMethod)) {
            val target = mutableClassDefBy(method.definingClass).methods.single {
                it.name == method.name && it.returnType == method.returnType && it.parameterTypes == method.parameterTypes
            }
            target.forceReturn("const/4 v0, 0x0\nreturn v0")
        }
        val pickerCallback = mutableClassDefBy(
            "Landroidx/credentials/playservices/HiddenActivity\$handleGetSignInIntent\$1\$1;",
        ).methods.single {
            it.name == "invoke" && it.returnType == "V" &&
                it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/app/PendingIntent;")
        }
        val pickerInstructions = pickerCallback.implementation!!.instructions
        val pickerLaunch = pickerInstructions.indices.single { index ->
            val instruction = pickerInstructions[index]
            instruction.opcode == Opcode.INVOKE_VIRTUAL_RANGE &&
                (instruction as ReferenceInstruction).reference.toString() ==
                "Landroid/app/Activity;->startIntentSenderForResult(Landroid/content/IntentSender;ILandroid/content/Intent;IIILandroid/os/Bundle;)V"
        }
        val range = pickerInstructions[pickerLaunch] as RegisterRangeInstruction
        check(range.registerCount == 8)
        val start = range.startRegister
        pickerCallback.replaceInstruction(pickerLaunch, "invoke-static/range {v$start .. v${start + 7}}, " +
            "Lapp/browzomje/extension/pinterest/MicroGSignIn;->launch(Landroid/app/Activity;" +
            "Landroid/content/IntentSender;ILandroid/content/Intent;IIILandroid/os/Bundle;)V")

        PatchLog.hooked(PATCH_NAME, mutableClassDefBy(frameworkMethod.definingClass).methods.single {
            it.name == frameworkMethod.name && it.parameterTypes.isEmpty() && it.returnType == "Z"
        }, "route credential provider and messaging to microG-RE")
    }
}
