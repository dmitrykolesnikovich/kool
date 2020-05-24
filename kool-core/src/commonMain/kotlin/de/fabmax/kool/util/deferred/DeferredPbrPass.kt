package de.fabmax.kool.util.deferred

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.OffscreenRenderPass2d
import de.fabmax.kool.scene.*

class DeferredPbrPass(scene: Scene, mrtPass: DeferredMrtPass, cfg: DeferredPbrShader.DeferredPbrConfig = DeferredPbrShader.DeferredPbrConfig()) :
        OffscreenRenderPass2d(Group(), mrtPass.texWidth, mrtPass.texHeight) {

    init {
        scene.onRenderScene += { ctx ->
            val mapW = mainRenderPass.viewport.width
            val mapH = mainRenderPass.viewport.height
            if (mapW > 0 && mapH > 0 && (mapW != texWidth || mapH != texHeight)) {
                resize(mapW, mapH, ctx)
                mrtPass.resize(mapW, mapH, ctx)
            }
        }
        lighting = scene.lighting

        camera = OrthographicCamera().apply {
            projCorrectionMode = Camera.ProjCorrectionMode.OFFSCREEN
            isKeepAspectRatio = false
            left = 0f
            right = 1f
            top = 1f
            bottom = 0f
        }

        (drawNode as Group).apply {
            +mesh(listOf(Attribute.POSITIONS, Attribute.TEXTURE_COORDS)) {
                generate {
                    rect {
                        size.set(1f, 1f)
                        mirrorTexCoordsY()
                    }
                }

                cfg.apply {
                    if (sceneCamera == null) { sceneCamera = mrtPass.camera }

                    if (positionAo == null) { positionAo = mrtPass.positionAo }
                    if (normalRoughness == null) { normalRoughness = mrtPass.normalRoughness }
                    if (albedoMetal == null) { albedoMetal = mrtPass.albedoMetal }
                }

                pipelineLoader = DeferredPbrShader(cfg)
            }
        }
    }

    override fun dispose(ctx: KoolContext) {
        drawNode.dispose(ctx)
        super.dispose(ctx)
    }
}
