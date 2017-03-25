package de.fabmax.kool.scene.ui

import de.fabmax.kool.platform.RenderContext
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.MeshData
import de.fabmax.kool.scene.Node
import de.fabmax.kool.shading.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MeshBuilder
import de.fabmax.kool.util.RayTest

/**
 * @author fabmax
 */
abstract class Background(val component: UiComponent) : Node() {

    abstract fun updateComponentAlpha()

    abstract fun update(ctx: RenderContext)

}

open class SimpleBackground(component: UiComponent, val bgShader: BasicShader = basicShader {
    lightModel = LightModel.PHONG_LIGHTING
    colorModel = ColorModel.STATIC_COLOR
    isAlpha = true
}) : Background(component) {

    protected val meshData = MeshData(true, true, true)
    protected val meshBuilder = MeshBuilder(meshData)
    protected val mesh = Mesh(meshData).apply {
        parent = this@SimpleBackground
        shader = bgShader
    }

    var color = Color.BLACK
        set(value) {
            field = value
            val shader = mesh.shader
            if (shader is BasicShader) {
                shader.staticColor.set(value)
            }
        }

    init {
        // set background color to update color property of shader
        color = Color.BLACK
    }

    override fun update(ctx: RenderContext) {
        component.setupBuilder(meshBuilder)
        meshBuilder.color = color
        meshBuilder.rect {
            size.set(component.width, component.height)
            fullTexCoords()
        }
        bounds.clear()
        bounds.add(mesh.bounds)
    }

    override fun updateComponentAlpha() {
        bgShader.alpha = component.alpha
    }

    override fun render(ctx: RenderContext) {
        super.render(ctx)
        mesh.render(ctx)
    }

    override fun dispose(ctx: RenderContext) {
        super.dispose(ctx)
        mesh.dispose(ctx)
    }

    override fun rayTest(test: RayTest) {
        super.rayTest(test)
        mesh.rayTest(test)
    }
}

open class BlurredBackground(component: UiComponent, val blurShader: BlurShader = blurShader {
    lightModel = LightModel.PHONG_LIGHTING
    colorModel = ColorModel.STATIC_COLOR
    isAlpha = true
}) : SimpleBackground(component, blurShader) {

    override fun render(ctx: RenderContext) {
        if (blurShader.blurHelper == null) {
            blurShader.blurHelper = component.root.createBlurHelper()
        }
        super.render(ctx)
    }
}
