package de.fabmax.kool.platform.gl

import de.fabmax.kool.drawqueue.DrawQueue
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.platform.Lwjgl3Context
import de.fabmax.kool.util.Float32BufferImpl
import de.fabmax.kool.util.createFloat32Buffer
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL31.glClearBufferfv
import org.lwjgl.opengl.GL31.glDrawElementsInstanced

class QueueRendererGl(backend: GlRenderBackend, val ctx: Lwjgl3Context) {

    private val glAttribs = GlAttribs()
    private val shaderMgr = ShaderManager(backend, ctx)

    private val colorBuffer = createFloat32Buffer(4) as Float32BufferImpl

    fun disposePipelines(pipelines: List<Pipeline>) {
        pipelines.forEach {
            shaderMgr.deleteShader(it)
        }
    }

    fun renderQueue(queue: DrawQueue) {
        queue.renderPass.apply {
            glViewport(viewport.x, viewport.y, viewport.width, viewport.height)

            if (this is OffscreenRenderPass2dMrt) {
                for (i in 0 until nAttachments) {
                    (clearColors[i] ?: clearColor)?.let {
                        colorBuffer[0] = it.r
                        colorBuffer[1] = it.g
                        colorBuffer[2] = it.b
                        colorBuffer[3] = it.a
                        glClearBufferfv(GL_COLOR, i, colorBuffer.buffer)
                    }
                }
                if (clearDepth) {
                    glClear(GL_DEPTH_BUFFER_BIT)
                }

            } else {
                clearColor?.let { glClearColor(it.r, it.g, it.b, it.a) }
                val clearMask = clearMask()
                if (clearMask != 0) {
                    glClear(clearMask)
                }
            }
        }

        for (cmd in queue.commands) {
            cmd.pipeline?.let { pipeline ->
                glAttribs.setupPipelineAttribs(pipeline)

                if (cmd.mesh.geometry.numIndices > 0) {
                    shaderMgr.setupShader(cmd)?.let {
                        if (it.primitiveType != 0 && it.indexType != 0) {
                            val insts = cmd.mesh.instances
                            if (insts == null) {
                                glDrawElements(it.primitiveType, it.numIndices, it.indexType, 0)
                                ctx.engineStats.addPrimitiveCount(cmd.mesh.geometry.numPrimitives)
                            } else if (insts.numInstances > 0) {
                                glDrawElementsInstanced(it.primitiveType, it.numIndices, it.indexType, 0, insts.numInstances)
                                ctx.engineStats.addPrimitiveCount(cmd.mesh.geometry.numPrimitives * insts.numInstances)
                            }
                            ctx.engineStats.addDrawCommandCount(1)
                        }
                    }
                }
            }
        }
    }

    private inner class GlAttribs {
        var actIsWriteDepth = true
        var actDepthTest: DepthCompareOp? = null
        var actCullMethod: CullMethod? = null
        var lineWidth = 0f

        fun setupPipelineAttribs(pipeline: Pipeline) {
            setBlendMode(pipeline.blendMode)
            setDepthTest(pipeline.depthCompareOp)
            setWriteDepth(pipeline.isWriteDepth)
            setCullMethod(pipeline.cullMethod)
            if (lineWidth != pipeline.lineWidth) {
                lineWidth = pipeline.lineWidth
                glLineWidth(pipeline.lineWidth)
            }
        }

        private fun setCullMethod(cullMethod: CullMethod) {
            if (this.actCullMethod != cullMethod) {
                this.actCullMethod = cullMethod
                when (cullMethod) {
                    CullMethod.DEFAULT -> {
                        glEnable(GL_CULL_FACE)
                        glCullFace(GL_BACK)
                    }
                    CullMethod.CULL_BACK_FACES -> {
                        glEnable(GL_CULL_FACE)
                        glCullFace(GL_BACK)
                    }
                    CullMethod.CULL_FRONT_FACES -> {
                        glEnable(GL_CULL_FACE)
                        glCullFace(GL_FRONT)
                    }
                    CullMethod.NO_CULLING -> glDisable(GL_CULL_FACE)
                }
            }
        }

        private fun setWriteDepth(enabled: Boolean) {
            if (actIsWriteDepth != enabled) {
                actIsWriteDepth = enabled
                glDepthMask(enabled)
            }
        }

        private fun setDepthTest(depthCompareOp: DepthCompareOp) {
            if (actDepthTest != depthCompareOp) {
                actDepthTest = depthCompareOp
                when (depthCompareOp) {
                    DepthCompareOp.DISABLED -> glDisable(GL_DEPTH_TEST)
                    DepthCompareOp.ALWAYS -> {
                        glEnable(GL_DEPTH_TEST)
                        glDepthFunc(GL_ALWAYS)
                    }
                    DepthCompareOp.LESS -> {
                        glEnable(GL_DEPTH_TEST)
                        glDepthFunc(GL_LESS)
                    }
                    DepthCompareOp.LESS_EQUAL -> {
                        glEnable(GL_DEPTH_TEST)
                        glDepthFunc(GL_LEQUAL)
                    }
                    DepthCompareOp.GREATER -> {
                        glEnable(GL_DEPTH_TEST)
                        glDepthFunc(GL_GREATER)
                    }
                    DepthCompareOp.GREATER_EQUAL -> {
                        glEnable(GL_DEPTH_TEST)
                        glDepthFunc(GL_GEQUAL)
                    }
                }
            }
        }

        private fun setBlendMode(blendMode: BlendMode) {
            when (blendMode) {
                BlendMode.DISABLED -> glDisable(GL_BLEND)
                BlendMode.BLEND_ADDITIVE -> {
                    glBlendFunc(GL_ONE, GL_ONE)
                    glEnable(GL_BLEND)
                }
                BlendMode.BLEND_MULTIPLY_ALPHA -> {
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                    glEnable(GL_BLEND)
                }
                BlendMode.BLEND_PREMULTIPLIED_ALPHA -> {
                    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
                    glEnable(GL_BLEND)
                }
            }
        }
    }

    private fun RenderPass.clearMask(): Int {
        var mask = 0
        if (clearDepth) {
            mask = GL_DEPTH_BUFFER_BIT
        }
        if (clearColor != null) {
            mask = mask or GL_COLOR_BUFFER_BIT
        }
        return mask
    }
}