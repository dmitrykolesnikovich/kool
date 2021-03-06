package de.fabmax.kool.pipeline

import de.fabmax.kool.KoolContext
import de.fabmax.kool.pipeline.shading.CustomShader
import de.fabmax.kool.platform.vk.pipeline.ShaderStage
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.util.Log
import org.lwjgl.vulkan.VK10
import java.io.FileNotFoundException

actual class ShaderCode private constructor(private val vkCode: VkCode?, private val glCode: GlCode?) {

    constructor(vararg stages: ShaderStage): this(VkCode(stages.asList()), null)
    constructor(vkCode: VkCode): this(vkCode, null)
    constructor(glCode: GlCode): this(null, glCode)

    actual val longHash: ULong = vkCode?.longHash ?: glCode!!.longHash

    val vkStages: List<ShaderStage>
        get() = vkCode!!.stages

    val glVertexSrc: String
        get() = glCode!!.vertexSrc
    val glFragmentSrc: String
        get() = glCode!!.fragmentSrc

    class GlCode(val vertexSrc: String, val fragmentSrc: String) {
        val longHash = (vertexSrc.hashCode().toULong() shl 32) + fragmentSrc.hashCode().toULong()
    }

    class VkCode(val stages: List<ShaderStage>) {
        val longHash : ULong
        init {
            var hash = 0UL
            stages.forEach { hash = (hash * 71023UL) xor it.longHash }
            longHash = hash
        }
    }

    companion object {
        fun codeFromSource(vertShaderSrc: String, fragShaderSrc: String): ShaderCode {
            val vertShaderCode = ShaderStage.fromSource("vertShader", vertShaderSrc, VK10.VK_SHADER_STAGE_VERTEX_BIT)
            val fragShaderCode = ShaderStage.fromSource("fragShader", fragShaderSrc, VK10.VK_SHADER_STAGE_FRAGMENT_BIT)

            Log.d("ShaderCode") { "Successfully compiled shader: vertShader: ${vertShaderCode.code.size} bytes, fragShader: ${fragShaderCode.code.size} bytes" }
            return ShaderCode(vertShaderCode, fragShaderCode)
        }

        fun codeFromResources(vertShaderName: String, fragShaderName: String): ShaderCode {
            val vertShaderCode = this::class.java.classLoader.getResourceAsStream(vertShaderName)?.use {
                ShaderStage.fromSource(vertShaderName, it, VK10.VK_SHADER_STAGE_VERTEX_BIT)
            } ?: throw FileNotFoundException("vertex shader resource not found: $vertShaderName")

            val fragShaderCode = this::class.java.classLoader.getResourceAsStream(fragShaderName)?.use {
                ShaderStage.fromSource(fragShaderName, it, VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
            } ?: throw FileNotFoundException("fragment shader resource not found: $vertShaderName")

            Log.d("ShaderCode") { "Successfully compiled shader: $vertShaderName: ${vertShaderCode.code.size} bytes, $fragShaderName: ${fragShaderCode.code.size} bytes" }

            return ShaderCode(vertShaderCode, fragShaderCode)
        }

        fun shaderFromResources(vertShaderName: String, fragShaderName: String): (Mesh, Pipeline.BuildContext, KoolContext) -> CustomShader = { _, _, _ ->
            CustomShader(codeFromResources(vertShaderName, fragShaderName))
        }
    }
}