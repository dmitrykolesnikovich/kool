package de.fabmax.kool.platform.vk

import de.fabmax.kool.platform.Lwjgl3Context
import de.fabmax.kool.platform.VkRenderBackend
import de.fabmax.kool.platform.vk.pipeline.PipelineManager
import de.fabmax.kool.util.logD
import org.lwjgl.glfw.GLFW.glfwGetFramebufferSize
import org.lwjgl.glfw.GLFW.glfwWaitEvents
import org.lwjgl.vulkan.VK10.vkDeviceWaitIdle

class VkSystem(val setup: VkSetup = VkSetup(), val scene: VkScene, backend: VkRenderBackend, val ctx: Lwjgl3Context) : VkResource() {

    val window: GlfwWindow

    val instance: Instance
    val physicalDevice: PhysicalDevice
    val device: Device
    val memManager: MemoryManager
    val pipelineManager = PipelineManager(this)

    val commandPool: CommandPool
    val transferCommandPool: CommandPool

    val renderLoop: RenderLoop

    var swapChain: SwapChain? = null

    init {
        window = GlfwWindow(this, backend.windowWidth, backend.windowHeight)
        instance = Instance(this)
        window.createSurface()

        physicalDevice = PhysicalDevice(this)
        device = Device(this)
        memManager = MemoryManager(this)
        commandPool = CommandPool(this, device.graphicsQueue)
        transferCommandPool = CommandPool(this, device.transferQueue)
        //transferCommandPool = commandPool

        scene.onLoad(this)

        renderLoop = RenderLoop(this)
        recreateSwapChain()

        //memManager.printMemoryStats()
    }

    fun run() {
        renderLoop.run()
        destroy()
    }

    fun recreateSwapChain() {
        memStack {
            val width = mallocInt(1)
            val height = mallocInt(1)
            while (width[0] == 0 || height[0] == 0) {
                // wait while window is minimized
                glfwGetFramebufferSize(window.glfwWindow, width, height)
                glfwWaitEvents()
            }
        }

        swapChain?.let {
            pipelineManager.onSwapchainDestroyed()
            vkDeviceWaitIdle(device.vkDevice)
            it.destroy()
        }
        swapChain = SwapChain(this@VkSystem).also {
            pipelineManager.onSwapchainCreated(it)
            scene.onSwapChainCreated(it)
        }
    }

    override fun freeResources() {
        logD { "Destroyed VkSystem" }
    }
}