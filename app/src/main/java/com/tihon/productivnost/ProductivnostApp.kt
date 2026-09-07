package com.tihon.productivnost

import android.app.Application
import com.tihon.productivnost.core.PlantGeneratorSpecies
import com.tihon.productivnost.core.PlantKind
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.service.Notifications
import com.tihon.productivnost.service.ProductivityService
import com.tihon.productivnost.util.Permissions
import com.tihon.productivnost.voxel.PlantGenerator

class ProductivnostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifications.ensureChannels(this)
        PlantGeneratorSpecies.focus = PlantGenerator.speciesCount(PlantKind.FOCUS)
        PlantGeneratorSpecies.study = PlantGenerator.speciesCount(PlantKind.STUDY)
        if (Permissions.hasUsageAccess(this) || Store.focus.value != null || Store.study.value != null) {
            ProductivityService.start(this)
        }
    }
}
