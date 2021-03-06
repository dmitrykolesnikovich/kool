package de.fabmax.kool.pipeline

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.pipeline.shadermodel.*
import de.fabmax.kool.pipeline.shading.ModeledShader
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Node
import de.fabmax.kool.util.Color


open class DepthMapPass(drawNode: Node, width: Int, height: Int = width, setup: Setup = defaultSetup()) :
        OffscreenRenderPass2d(drawNode, width, height, setup) {
    private val shadowPipelines = mutableMapOf<Long, Pipeline?>()

    /**
     * Cull method to use for depth map rendering. If null (the default) the original cull method of meshes is used.
     */
    var cullMethod: CullMethod? = null

    init {
        type = Type.DEPTH
        onAfterCollectDrawCommands += { ctx ->
            // replace regular object shaders by cheaper shadow versions
            drawQueue.commands.forEach {
                it.pipeline = getShadowPipeline(it.mesh, ctx)
            }
        }
    }

    protected open fun getShadowPipeline(mesh: Mesh, ctx: KoolContext): Pipeline? {
        val culling = this.cullMethod ?: mesh.getPipeline(ctx)?.cullMethod ?: CullMethod.CULL_BACK_FACES
        return shadowPipelines.getOrPut(mesh.id) { createPipeline(mesh, culling, ctx) }
    }

    protected open fun createPipeline(mesh: Mesh, culling: CullMethod, ctx: KoolContext): Pipeline? {
        // create a minimal dummy shader for each attribute set
        val shadowShader = ModeledShader(ShaderModel("shadow shader").apply {
            vertexStage { positionOutput = simpleVertexPositionNode().outVec4 }
            fragmentStage { colorOutput(ShaderNodeIoVar(ModelVar4fConst(Vec4f(1f)))) }
        })
        val pipelineBuilder = Pipeline.Builder().apply {
            blendMode = BlendMode.DISABLED
            cullMethod = culling
        }
        return shadowShader.createPipeline(mesh, pipelineBuilder, ctx)
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        shadowPipelines.values.filterNotNull().forEach { ctx.disposePipeline(it) }
    }

    companion object {
        private fun defaultSetup(): Setup {
            return Setup().apply {
                colorFormat = TexFormat.R
                colorRenderTarget = RENDER_TARGET_RENDERBUFFER
                depthRenderTarget = RENDER_TARGET_TEXTURE
            }
        }
    }
}

class LinearDepthMapPass(drawNode: Node, width: Int, height: Int = width) : DepthMapPass(drawNode, width, height, linearDepthSetup()) {

    init {
        onAfterCollectDrawCommands += {
            clearColor = Color(1f, 0f, 0f, 1f)
        }
    }

    override fun createPipeline(mesh: Mesh, culling: CullMethod, ctx: KoolContext): Pipeline? {
        val shadowShader = ModeledShader(ShaderModel("shadow shader").apply {
            vertexStage { positionOutput = simpleVertexPositionNode().outVec4 }
            fragmentStage {
                val linDepth = addNode(LinearDepthNode(stage))
                colorOutput(linDepth.outColor)
            }
        })
        val pipelineBuilder = Pipeline.Builder().apply {
            blendMode = BlendMode.DISABLED
            cullMethod = culling
        }
        return shadowShader.createPipeline(mesh, pipelineBuilder, ctx)
    }

    private class LinearDepthNode(graph: ShaderGraph) : ShaderNode("linearDepth", graph) {
        val outColor = ShaderNodeIoVar(ModelVar4f("linearDepth"), this)

        override fun generateCode(generator: CodeGenerator) {
            generator.appendMain("""
                float d = gl_FragCoord.z / gl_FragCoord.w;
                ${outColor.declare()} = vec4(-d, 0.0, 0.0, 1.0);
            """)
        }
    }

    companion object {
        private fun linearDepthSetup(): Setup {
            return Setup().apply {
                colorFormat = TexFormat.R_F16
                colorRenderTarget = RENDER_TARGET_TEXTURE
                depthRenderTarget = RENDER_TARGET_RENDERBUFFER
            }
        }
    }
}

class NormalLinearDepthMapPass(drawNode: Node, width: Int, height: Int = width) : DepthMapPass(drawNode, width, height, normalLinearDepthSetup()) {

    init {
        name = "NormalLinearDepthMapPass"

        onAfterCollectDrawCommands += {
            clearColor = Color(0f, 1f, 0f, 1f)
        }
    }

    override fun createPipeline(mesh: Mesh, culling: CullMethod, ctx: KoolContext): Pipeline? {
        if (!mesh.geometry.hasAttribute(Attribute.NORMALS)) {
            return null
        }

        val shadowShader = ModeledShader(ShaderModel("shadow shader").apply {
            val ifNormals: StageInterfaceNode
            vertexStage {
                val mvpNode = mvpNode()

                val modelViewMat = multiplyNode(mvpNode.outModelMat, mvpNode.outViewMat).output
                val nrm = vec3TransformNode(attrNormals().output, modelViewMat, 0f)
                ifNormals = stageInterfaceNode("ifNormals", nrm.outVec3)

                positionOutput = vec4TransformNode(attrPositions().output, mvpNode.outMvpMat).outVec4
            }
            fragmentStage {
                val linDepth = addNode(NormalLinearDepthNode(ifNormals.output, stage))
                colorOutput(linDepth.outColor)
            }
        })
        val pipelineBuilder = Pipeline.Builder().apply {
            blendMode = BlendMode.DISABLED
            cullMethod = culling
        }
        return shadowShader.createPipeline(mesh, pipelineBuilder, ctx)
    }

    private class NormalLinearDepthNode(val inNormals: ShaderNodeIoVar, graph: ShaderGraph) : ShaderNode("normalLinearDepth", graph) {
        val outColor = ShaderNodeIoVar(ModelVar4f("normaLinearDepth"), this)

        override fun generateCode(generator: CodeGenerator) {
            generator.appendMain("""
                float d = gl_FragCoord.z / gl_FragCoord.w;
                ${outColor.declare()} = vec4(normalize(${inNormals.ref3f()}), -d);
            """)
        }
    }

    companion object {
        private fun normalLinearDepthSetup(): Setup {
            return Setup().apply {
                colorFormat = TexFormat.RGBA_F16
                colorRenderTarget = RENDER_TARGET_TEXTURE
                depthRenderTarget = RENDER_TARGET_RENDERBUFFER
            }
        }
    }
}
