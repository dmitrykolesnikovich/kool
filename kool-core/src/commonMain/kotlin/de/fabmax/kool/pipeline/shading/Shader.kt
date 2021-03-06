package de.fabmax.kool.pipeline.shading

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.Pipeline
import de.fabmax.kool.pipeline.ShaderCode

abstract class Shader {

    val onCreated = mutableListOf<((Pipeline) -> Unit)>()

    abstract fun generateCode(pipeline: Pipeline, ctx: KoolContext): ShaderCode

    open fun onPipelineCreated(pipeline: Pipeline) {
        onCreated.forEach { it(pipeline) }
    }

}
