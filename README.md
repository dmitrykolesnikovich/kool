#Kotlin + OpenGL = kool

A simple OpenGL based graphics engine, which works with Desktop Java as well as
in browsers with Javascript/WebGL (and also on Android once I add the platform
bindings)

For now this is nothing more than a simple experiment. However, if you are curious
you can clone this repo and open index.html in the ./out directory in your favourite
browser.

What's working:
- Mesh building functions for several primitive 3D shapes
- Shading with different light (phong / gouraud) and color models (vertex, texture or fixed)
- Transform groups and simple animation
- Mouse controlled camera
- *Text rendering using arbitrary fonts*. For now character set is fixed but theoretically it has unicode support

Below is the code for the demo scene

##Example:
```kotlin
fun main(args: Array<String>) {
    // Initialize platform
    PlatformImpl.init()
    
    // Create a context
    val ctx = Platform.createContext(Lwjgl3Context.InitProps())
    
    // Or for javascript:
    // val ctx = Platform.createContext(JsContext.InitProps())
    
    // Create scene contents
    ctx.scene.root = group {
        // Add a mouse-controlled camera manipulator (actually a specialized TransformGroup)
        +sphericalInputTransform {
            // Set some initial rotation so that we look down on the scene
            setRotation(20f, -30f)
            // Add camera to the transform group
            +ctx.scene.camera
        }

        // Add a TransformGroup with a bouncing sphere
        +transformGroup {
            // Animation function is called on every frame
            animation = { ctx ->
                // Clear transformation
                setIdentity()
                // Shift content 5 units left and let it bounce along Y-Axis
                translate(-5f, Math.sin(ctx.time * 5).toFloat(), 0f)
                // Slowly rotate the sphere, so we can see all the colors
                rotate(ctx.time.toFloat() * 19, Vec3f.X_AXIS)
            }

            // Add a sphere mesh
            +colorMesh {
                // vertexModFun is called for every generated vertex, overwrite the vertex color depending on the
                // normal orientation, this will make the sphere nicely colorful
                vertexModFun = { color.set(Color((normal.x + 1) / 2, (normal.y + 1) / 2, (normal.z + 1) / 2, 1f)) }
                // Generate the sphere mesh with a sphere radius of 1.5 units
                sphere {
                    radius = 1.5f
                    // Make it really smooth
                    steps = 50 
                }
            }
        }

        // Add a TransformGroup with a rotating cube
        +transformGroup {
            // Similar to above, but this time content is shifted to the right
            animation = { ctx ->
                setIdentity()
                translate(5f, 0f, 0f)
                rotate(ctx.time.toFloat() * 90, Vec3f.Y_AXIS)
                rotate(ctx.time.toFloat() * 19, Vec3f.X_AXIS)
            }

            // Add a cube mesh
            +colorMesh {
                // Make the generated mesh twice as large
                scale(2f, 2f, 2f)
                // Shift cube origin to center instead of lower, left, back corner
                translate(-.5f, -.5f, -.5f)

                // Generate cube mesh with every face set to a different color
                cube {
                    frontColor = Color.RED
                    rightColor = Color.GREEN
                    backColor = Color.BLUE
                    leftColor = Color.YELLOW
                    topColor = Color.MAGENTA
                    bottomColor = Color.CYAN
                }
            }
        }

        // Add another TransformGroup with a size-changing cylinder
        +transformGroup {
            // Content is shifted to the back and scaled depending on time
            animation = { ctx ->
                setIdentity()
                translate(0f, 0f, -5f)
                val s = 1f + Math.sin(ctx.time * 3).toFloat() * 0.25f
                scale(s, s, s)
            }

            // Add the text, you can use any font you like
            val textFont = Font("Segoe UI", 72.0f)
            +textMesh(textFont) {
                color = Color.LIME
                text {
                    // Set the text to be rendered, for now only characters defined in [Font.STD_CHARS] can be rendered
                    text = "kool Text!"
                    font = textFont
                    // Make the text centered
                    position.set(-font.stringWidth(text) / 2f, 0f, 0f)
                    // generated text mesh size is based on the font size, without scaling, a single character would
                    // be 48 units tall, hence we have to scale it down a lot. This could also be done by calling
                    // scale(0.03f, 0.03f, 0.03f) on the outer level, but let's take the shortcut
                    scale = 0.03f
                }
            }
        }
    }

    // Set background color
    ctx.clearColor = Color(0.05f, 0.15f, 0.25f, 1f)
    // Finally run the whole thing
    ctx.run()
}
```