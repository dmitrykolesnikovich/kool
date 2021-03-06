package de.fabmax.kool.platform.vk

import de.fabmax.kool.pipeline.OffscreenPassCubeImpl
import de.fabmax.kool.pipeline.OffscreenRenderPassCube
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.platform.Lwjgl3Context
import de.fabmax.kool.platform.vk.util.vkFormat
import org.lwjgl.util.vma.Vma
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import kotlin.math.pow
import kotlin.math.roundToInt

class VkOffscreenPassCube(val parentPass: OffscreenPassCubeImpl) : OffscreenPassCubeImpl.BackendImpl {
    private var isCreated = false

    var renderPass: VkOffscreenRenderPass? = null
        private set

    lateinit var image: Image
    lateinit var imageView: ImageView
    var sampler: Long = 0L

    override fun draw(ctx: Lwjgl3Context) {
        if (!isCreated) {
            create(ctx)
            isCreated = true
        }
    }

    override fun dispose(ctx: Lwjgl3Context) {
        val rp = renderPass
        val loadedColorTex = parentPass.texture.loadedTexture

        isCreated = false
        renderPass = null
        parentPass.texture.clear()

        ctx.runDelayed(3) {
            rp?.destroyNow()
            loadedColorTex?.dispose()
        }
    }

    private fun Texture.clear() {
        loadedTexture = null
        loadingState = Texture.LoadingState.NOT_LOADED
    }

    override fun resize(width: Int, height: Int, ctx: Lwjgl3Context) {
        dispose(ctx)
        create(ctx)
    }

    fun transitionTexLayout(commandBuffer: VkCommandBuffer, dstLayout: Int) {
        memStack {
            image.transitionLayout(this, commandBuffer, dstLayout)
        }
    }

    fun generateMipmaps(commandBuffer: VkCommandBuffer, dstLayout: Int) {
        if (parentPass.offscreenPass.mipLevels > 1) {
            memStack {
                image.generateMipmaps(this, commandBuffer, dstLayout)
            }
        }
    }

    fun copyView(commandBuffer: VkCommandBuffer, viewDir: OffscreenRenderPassCube.ViewDirection) {
        val rp = renderPass ?: return

        var mipLevel = 0
        var width = parentPass.offscreenPass.texWidth
        var height = parentPass.offscreenPass.texHeight
        if (parentPass.offscreenPass.targetMipLevel > 0) {
            width = (width * 0.5.pow(parentPass.offscreenPass.targetMipLevel)).roundToInt()
            height = (height * 0.5.pow(parentPass.offscreenPass.targetMipLevel)).roundToInt()
            mipLevel = parentPass.offscreenPass.targetMipLevel
        }

        memStack {
            rp.image.transitionLayout(this, commandBuffer, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)

            val layer = VIEW_TO_CUBE_LAYER_MAP[viewDir.index]
            val imageCopy = callocVkImageCopyN(1) {
                srcSubresource {
                    it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    it.mipLevel(0)
                    it.baseArrayLayer(0)
                    it.layerCount(1)
                }
                srcOffset { it.set(0, 0, 0) }
                dstSubresource {
                    it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    it.mipLevel(mipLevel)
                    it.baseArrayLayer(layer)
                    it.layerCount(1)
                }
                dstOffset { it.set(0, 0, 0) }
                extent { it.set(width, height, 1) }
            }
            vkCmdCopyImage(commandBuffer, rp.image.vkImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image.vkImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, imageCopy)
            rp.image.transitionLayout(this, commandBuffer, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            parentPass.texture.loadingState = Texture.LoadingState.LOADED
        }
    }

    private fun create(ctx: Lwjgl3Context) {
        val sys = (ctx.renderBackend as VkRenderBackend).vkSystem
        val rp = VkOffscreenRenderPass(sys, parentPass.offscreenPass.texWidth, parentPass.offscreenPass.texHeight, true, parentPass.offscreenPass.colorFormat.vkFormat)
        createTex(rp, sys)
        renderPass = rp
    }

    private fun createTex(rp: VkOffscreenRenderPass, sys: VkSystem) {
        val imgConfig = Image.Config()
        imgConfig.width = rp.maxWidth
        imgConfig.height = rp.maxHeight
        imgConfig.mipLevels = parentPass.offscreenPass.mipLevels
        imgConfig.numSamples = VK_SAMPLE_COUNT_1_BIT
        imgConfig.format = rp.colorFormats[0]
        imgConfig.tiling = VK_IMAGE_TILING_OPTIMAL
        imgConfig.usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT
        imgConfig.allocUsage = Vma.VMA_MEMORY_USAGE_GPU_ONLY
        imgConfig.arrayLayers = 6
        imgConfig.flags = VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT

        image = Image(sys, imgConfig)
        imageView = ImageView(sys, image.vkImage, image.format, VK_IMAGE_ASPECT_COLOR_BIT, image.mipLevels, VK_IMAGE_VIEW_TYPE_CUBE)
        sampler = createSampler(sys, image)

        image.transitionLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

        val loadedTex = LoadedTextureVk(sys, rp.texFormat, image, imageView, sampler)
        rp.addDependingResource(loadedTex)

        parentPass.texture.apply {
            loadedTexture = loadedTex
            loadingState = Texture.LoadingState.LOADING
        }
    }

    private fun createSampler(sys: VkSystem, texImage: Image): Long {
        memStack {
            val samplerInfo = callocVkSamplerCreateInfo {
                sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                magFilter(VK_FILTER_LINEAR)
                minFilter(VK_FILTER_LINEAR)
                addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                anisotropyEnable(false)
                maxAnisotropy(1f)
                borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                unnormalizedCoordinates(false)
                compareEnable(false)
                compareOp(VK_COMPARE_OP_ALWAYS)
                mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                mipLodBias(0f)
                minLod(0f)
                maxLod(texImage.mipLevels.toFloat())
            }
            val ptr = mallocLong(1)
            check(vkCreateSampler(sys.device.vkDevice, samplerInfo, null, ptr) == VK_SUCCESS)
            return ptr[0]
        }
    }

    companion object {
        private val VIEW_TO_CUBE_LAYER_MAP = IntArray(6) { i ->
            when (i) {
                OffscreenRenderPassCube.ViewDirection.RIGHT.index -> 0
                OffscreenRenderPassCube.ViewDirection.LEFT.index -> 1
                OffscreenRenderPassCube.ViewDirection.UP.index -> 2
                OffscreenRenderPassCube.ViewDirection.DOWN.index -> 3
                OffscreenRenderPassCube.ViewDirection.FRONT.index -> 4
                OffscreenRenderPassCube.ViewDirection.BACK.index -> 5
                else -> 0
            }
        }
    }
}
