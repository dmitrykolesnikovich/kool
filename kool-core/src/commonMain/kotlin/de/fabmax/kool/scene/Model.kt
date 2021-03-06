package de.fabmax.kool.scene

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.Texture

class Model(name: String? = null) : TransformGroup(name) {

    val nodes = mutableMapOf<String, TransformGroup>()
    val meshes = mutableMapOf<String, Mesh>()
    val textures = mutableMapOf<String, Texture>()

    fun printHierarchy() {
        printHierarchy("")
    }

    private fun TransformGroup.printHierarchy(indent: String) {
        println("$indent$name")
        children.forEach {
            if (it is TransformGroup) {
                it.printHierarchy("$indent    ")
            } else {
                println("$indent    ${it.name}")
            }
        }
    }

    override fun dispose(ctx: KoolContext) {
        textures.values.forEach { it.dispose() }
        super.dispose(ctx)
    }
}