package de.fabmax.kool.platform

import de.fabmax.kool.shading.*

/**
 * @author fabmax
 */
abstract class ShaderGenerator {

    companion object {
        val UNIFORM_MVP_MATRIX = "uMvpMatrix"
        val UNIFORM_MODEL_MATRIX = "uModelMatrix"
        val UNIFORM_VIEW_MATRIX = "uViewMatrix"
        val UNIFORM_LIGHT_DIRECTION = "uLightDirection"
        val UNIFORM_LIGHT_COLOR = "uLightColor"
        val UNIFORM_SHININESS = "uShininess"
        val UNIFORM_SPECULAR_INTENSITY = "uSpecularIntensity"
        val UNIFORM_CAMERA_POSITION = "uCameraPosition"
        val UNIFORM_FOG_COLOR = "uFogColor"
        val UNIFORM_FOG_RANGE = "uFogRange"
        val UNIFORM_TEXTURE_0 = "uTexture0"
        val UNIFORM_STATIC_COLOR = "uStaticColor"
        val UNIFORM_ALPHA = "uAlpha"
        val UNIFORM_SATURATION = "uSaturation"

        val ATTRIBUTE_NAME_POSITION = "aVertexPosition_modelspace"
        val ATTRIBUTE_NAME_NORMAL = "aVertexNormal_modelspace"
        val ATTRIBUTE_NAME_TEX_COORD = "aVertexTexCoord"
        val ATTRIBUTE_NAME_COLOR = "aVertexColor"

        val VARYING_NAME_TEX_COORD = "vTexCoord"
        val VARYING_NAME_EYE_DIRECTION = "vEyeDirection_cameraspace"
        val VARYING_NAME_LIGHT_DIRECTION = "vLightDirection_cameraspace"
        val VARYING_NAME_NORMAL = "vNormal_cameraspace"
        val VARYING_NAME_COLOR = "vFragmentColor"
        val VARYING_NAME_DIFFUSE_LIGHT_COLOR = "vDiffuseLightColor"
        val VARYING_NAME_SPECULAR_LIGHT_COLOR = "vSpecularLightColor"
        val VARYING_NAME_POSITION_WORLDSPACE = "vPositionWorldspace"

        val LOCAL_NAME_FRAG_COLOR = "fragColor"
        val LOCAL_NAME_TEX_COLOR = "texColor"
        val LOCAL_NAME_VERTEX_COLOR = "vertColor"
        val LOCAL_NAME_STATIC_COLOR = "staticColor"
    }

    interface GlslInjector {
        fun vsStart(shaderProps: ShaderProps, text: StringBuilder) { }
        fun vsAfterInput(shaderProps: ShaderProps, text: StringBuilder) { }
        fun vsEnd(shaderProps: ShaderProps, text: StringBuilder) { }

        fun fsStart(shaderProps: ShaderProps, text: StringBuilder) { }
        fun fsAfterInput(shaderProps: ShaderProps, text: StringBuilder) { }
        fun fsEnd(shaderProps: ShaderProps, text: StringBuilder) { }

        fun fsBeforeSampling(shaderProps: ShaderProps, text: StringBuilder) { }
        fun fsAfterSampling(shaderProps: ShaderProps, text: StringBuilder) { }
    }

    val injectors: MutableList<GlslInjector> = mutableListOf()

    val uniformMvpMatrix: UniformMatrix4 = UniformMatrix4(UNIFORM_MVP_MATRIX)
    val uniformModelMatrix: UniformMatrix4 = UniformMatrix4(UNIFORM_MODEL_MATRIX)
    val uniformViewMatrix: UniformMatrix4 = UniformMatrix4(UNIFORM_VIEW_MATRIX)
    val uniformLightColor: Uniform3f = Uniform3f(UNIFORM_LIGHT_COLOR)
    val uniformLightDirection: Uniform3f = Uniform3f(UNIFORM_LIGHT_DIRECTION)
    val uniformCameraPosition: Uniform3f = Uniform3f(UNIFORM_CAMERA_POSITION)
    val uniformShininess: Uniform1f = Uniform1f(UNIFORM_SHININESS)
    val uniformSpecularIntensity: Uniform1f = Uniform1f(UNIFORM_SPECULAR_INTENSITY)
    val uniformStaticColor: Uniform4f = Uniform4f(UNIFORM_STATIC_COLOR)
    val uniformTexture: UniformTexture2D = UniformTexture2D(UNIFORM_TEXTURE_0)
    val uniformAlpha: Uniform1f = Uniform1f(UNIFORM_ALPHA)
    val uniformSaturation: Uniform1f = Uniform1f(UNIFORM_SATURATION)
    val uniformFogRange: Uniform1f = Uniform1f(UNIFORM_FOG_RANGE)
    val uniformFogColor: Uniform4f = Uniform4f(UNIFORM_FOG_COLOR)

    fun generate(shaderProps: ShaderProps): Shader.Source {
        uniformMvpMatrix.location = null
        uniformModelMatrix.location = null
        uniformViewMatrix.location = null
        uniformLightColor.location = null
        uniformLightDirection.location = null
        uniformCameraPosition.location = null
        uniformShininess.location = null
        uniformSpecularIntensity.location = null
        uniformStaticColor.location = null
        uniformTexture.location = null
        uniformAlpha.location = null
        uniformSaturation.location = null
        uniformFogRange.location = null
        uniformFogColor.location = null

        return generateSource(shaderProps)
    }

    abstract fun onLoad(shader: BasicShader, ctx: RenderContext)

    protected abstract fun generateSource(shaderProps: ShaderProps): Shader.Source

}
