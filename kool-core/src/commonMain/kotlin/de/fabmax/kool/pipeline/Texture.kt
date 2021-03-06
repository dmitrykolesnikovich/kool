package de.fabmax.kool.pipeline

import de.fabmax.kool.AssetManager
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Uint8Buffer
import de.fabmax.kool.util.createUint8Buffer
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt

/**
 * Describes a texture by it's properties and a loader function which is called once the texture is used.
 */
open class Texture(val name: String? = null, val props: TextureProps = TextureProps(), val loader: (suspend CoroutineScope.(AssetManager) -> TextureData)?) {

    /**
     * Contains the platform specific handle to the loaded texture. It is available after the loader function was
     * called by the texture manager.
     */
    var loadedTexture: LoadedTexture? = null

    var loadingState = LoadingState.NOT_LOADED

    fun dispose() {
        loadedTexture?.dispose()
        loadedTexture = null
        loadingState = LoadingState.NOT_LOADED
    }

    override fun toString(): String {
        return "Texture(name: $name)"
    }

    enum class LoadingState {
        NOT_LOADED,
        LOADING,
        LOADED,
        LOADING_FAILED
    }

    companion object {
        fun estimatedTexSize(width: Int, height: Int, bytesPerPx: Int, layers: Int, mipLevels: Int): Int {
            var mipFac = 1.0
            var mipAdd = 0.25
            for (i in 2..mipLevels) {
                mipFac += mipAdd
                mipAdd *= 0.25
            }
            return (width * height * layers * bytesPerPx * mipFac).roundToInt()
        }
    }
}

class SingleColorTexture(color: Color) : Texture(
        color.toString(),
        TextureProps(
                minFilter = FilterMethod.NEAREST,
                magFilter = FilterMethod.NEAREST,
                mipMapping = false,
                maxAnisotropy = 1),
        loader = { getColorTextureData(color) }) {

    companion object {
        private val colorData = mutableMapOf<Color, BufferedTextureData>()

        private fun getColorTextureData(color: Color): BufferedTextureData {
            return colorData.getOrPut(color) { BufferedTextureData.singleColor(color) }
        }
    }
}

open class CubeMapTexture(name: String? = null, props: TextureProps = TextureProps(), loader: (suspend CoroutineScope.(AssetManager) -> CubeMapTextureData)?) :
        Texture(name, props, loader)

data class TextureProps(
        val format: TexFormat = TexFormat.RGBA,
        val addressModeU: AddressMode = AddressMode.REPEAT,
        val addressModeV: AddressMode = AddressMode.REPEAT,
        val addressModeW: AddressMode = AddressMode.REPEAT,
        val minFilter: FilterMethod = FilterMethod.LINEAR,
        val magFilter: FilterMethod = FilterMethod.LINEAR,
        val mipMapping: Boolean = true,
        val maxAnisotropy: Int = 16)

enum class FilterMethod {
    NEAREST,
    LINEAR
}

enum class AddressMode {
    CLAMP_TO_BORDER,
    CLAMP_TO_EDGE,
    MIRRORED_REPEAT,
    REPEAT
}

abstract class TextureData {
    var width = 0
        protected set
    var height = 0
        protected set
    var format = TexFormat.RGBA
        protected set

    abstract val data: Any
}

/**
 * Byte buffer based texture data. Texture data can be generated and edited procedurally. Layout and format of data
 * is specified by the format parameter. The buffer size must match the texture size and data format.
 *
 * @param buffer texture data buffer, must have a size of width * height * bytes-per-pixel
 * @param width  width of texture in pixels
 * @param height height of texture in pixels
 * @param format texture data format
 */
open class BufferedTextureData(buffer: Uint8Buffer, width: Int, height: Int, format: TexFormat) : TextureData() {
    init {
        this.width = width
        this.height = height
        this.format = format
    }

    override val data: Uint8Buffer = buffer

    companion object {
        fun singleColor(color: Color): BufferedTextureData {
            val buf = createUint8Buffer(4)
            buf[0] = (color.r * 255f).roundToInt().toByte()
            buf[1] = (color.g * 255f).roundToInt().toByte()
            buf[2] = (color.b * 255f).roundToInt().toByte()
            buf[3] = (color.a * 255f).roundToInt().toByte()
            return BufferedTextureData(buf, 1, 1, TexFormat.RGBA)
        }
    }
}

class CubeMapTextureData(val front: TextureData, val back: TextureData, val left: TextureData,
                         val right: TextureData, val up: TextureData, val down: TextureData) : TextureData() {
    init {
        width = front.width
        height = front.height
        format = front.format
    }

    override val data: Any
        get() = front.data
}