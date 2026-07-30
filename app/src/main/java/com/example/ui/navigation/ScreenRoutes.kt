package com.example.ui.navigation

sealed class ScreenRoutes(val route: String) {
    object Splash : ScreenRoutes("splash")
    object Home : ScreenRoutes("home")
    object CameraScan : ScreenRoutes("camera_scan?folderId={folderId}&isColorScan={isColorScan}") {
        fun createRoute(folderId: Long? = null, isColorScan: Boolean = false) =
            "camera_scan?folderId=${folderId ?: -1L}&isColorScan=$isColorScan"
    }
    
    object EditDocument : ScreenRoutes("edit_document/{documentId}/{pageIndex}") {
        fun createRoute(documentId: Long, pageIndex: Int = 0) = "edit_document/$documentId/$pageIndex"
    }

    object SaveExport : ScreenRoutes("save_export/{documentId}") {
        fun createRoute(documentId: Long) = "save_export/$documentId"
    }

    object DocumentDetail : ScreenRoutes("doc_detail/{documentId}") {
        fun createRoute(documentId: Long) = "doc_detail/$documentId"
    }

    object PrivacyPolicy : ScreenRoutes("privacy_policy")
}
