package app.browzomje.patches.pinterest.education.hide_save_tooltip

import app.morphe.patcher.Fingerprint

// Final tooltip placement, independently of server experience ID and localized copy.
internal object SaveTooltipPlacementFingerprint : Fingerprint(
    definingClass = "Lcom/pinterest/education/view/EducationToolTipView;",
    returnType = "V",
    custom = { method, _ -> method.parameterTypes.size == 4 &&
        method.parameterTypes[1] == "Z" &&
        method.parameterTypes[2] == "Landroid/view/View;" &&
        method.parameterTypes[3] == "Z" },
)
