package de.fabmax.kool.util

import de.fabmax.kool.math.*
import de.fabmax.kool.pipeline.Attribute
import kotlin.math.*

/**
 * @author fabmax
 */
open class MeshBuilder(val geometry: IndexedVertexList) {

    val transform = Mat4fStack()

    var color = Color.BLACK
    var vertexModFun: (VertexView.() -> Unit)? = null

    private val hasNormals = geometry.hasAttribute(Attribute.NORMALS)

    private val tmpPos = MutableVec3f()
    private val tmpNrm = MutableVec3f()
    private val tmpUv = MutableVec2f()

    val circleProps = CircleProps()
    val cubeProps = CubeProps()
    val cylinderProps = CylinderProps()
    val gridProps = GridProps()
    val rectProps = RectProps()
    val sphereProps = SphereProps()
    var textProps: TextProps? = null

    open fun vertex(pos: Vec3f, nrm: Vec3f, uv: Vec2f = Vec2f.ZERO): Int {
        return geometry.addVertex {
            position.set(pos)
            normal.set(nrm)
            texCoord.set(uv)
            color.set(this@MeshBuilder.color)

            transform.transform(position)
            if (hasNormals && normal.sqrLength() != 0f) {
                transform.transform(normal, 0f)
                normal.norm()
            }

            vertexModFun?.invoke(this)
        }
    }

    inline fun withTransform(block: MeshBuilder.() -> Unit) {
        transform.push()
        this.block()
        transform.pop()
    }

    inline fun withColor(color: Color?, block: MeshBuilder.() -> Unit) {
        val c = this.color
        if (color != null) {
            this.color = color
        }
        this.block()
        this.color = c
    }

    fun clear() {
        geometry.clear()
        identity()
    }

    fun identity() = transform.setIdentity()

    fun translate(t: Vec3f) = transform.translate(t.x, t.y, t.z)

    fun translate(x: Float, y: Float, z: Float) = transform.translate(x, y, z)

    fun rotate(angleDeg: Float, axis: Vec3f) = transform.rotate(angleDeg, axis)

    fun rotate(angleDeg: Float, axX: Float, axY: Float, axZ: Float) = transform.rotate(angleDeg, axX, axY, axZ)

    fun scale(x: Float, y: Float, z: Float) = transform.scale(x, y, z)

    fun setCoordSystem(origin: Vec3f, right: Vec3f, up: Vec3f, top: Vec3f? = null) {
        var topV = top
        if (topV == null) {
            topV = cross(right, up)
        }

        transform.setIdentity()
        transform[0, 0] = right.x
        transform[1, 0] = right.y
        transform[2, 0] = right.z

        transform[0, 1] = up.x
        transform[1, 1] = up.y
        transform[2, 1] = up.z

        transform[0, 2] = topV.x
        transform[1, 2] = topV.y
        transform[2, 2] = topV.z

        transform[0, 3] = origin.x
        transform[1, 3] = origin.y
        transform[2, 3] = origin.z
    }

    inline fun circle(props: CircleProps.() -> Unit) {
        circleProps.defaults().props()
        circle(circleProps)
    }

    fun circle(props: CircleProps) {
        var i1 = 0
        val iCenter = vertex(props.center, Vec3f.Z_AXIS, props.uvCenter)
        for (i in 0..props.steps) {
            val ang = (props.startDeg + props.sweepDeg * i / props.steps).toRad()
            val cos = cos(ang)
            val sin = sin(ang)
            val px = props.center.x + props.radius * cos
            val py = props.center.y + props.radius * sin
            tmpUv.set(cos, -sin).scale(props.uvRadius).add(props.uvCenter)
            val idx = vertex(tmpPos.set(px, py, props.center.z), Vec3f.Z_AXIS, tmpUv)

            if (i > 0) {
                geometry.addTriIndices(iCenter, i1, idx)
            }
            i1 = idx
        }
    }

    inline fun sphere(props: SphereProps.() -> Unit) {
        sphereProps.defaults().props()
        sphere(sphereProps)
    }

    fun sphere(props: SphereProps) {
        val steps = max(props.steps / 2, 4)
        var prevIndices = IntArray(steps * 2 + 1)
        var rowIndices = IntArray(steps * 2 + 1)

        // bottom cap
        var theta = PI * (steps - 1) / steps
        var r = sin(theta).toFloat() * props.radius
        var y = cos(theta).toFloat() * props.radius
        for (i in 0..(steps * 2)) {
            val phi = PI * i / steps
            val x = cos(-phi).toFloat() * r
            val z = sin(-phi).toFloat() * r

            var uv = props.texCoordGenerator(theta.toFloat(), phi.toFloat())
            rowIndices[i] = vertex(tmpPos.set(x, y, z).add(props.center),
                    tmpNrm.set(x, y, z).scale(1f / props.radius), uv)

            if (i > 0) {
                uv = props.texCoordGenerator(PI.toFloat(), phi.toFloat())
                tmpPos.set(props.center.x, props.center.y-props.radius, props.center.z)
                val iCenter = vertex(tmpPos, Vec3f.NEG_Y_AXIS, uv)
                geometry.addTriIndices(iCenter, rowIndices[i], rowIndices[i - 1])
            }
        }

        // belt
        for (row in 2..steps-1) {
            val tmp = prevIndices
            prevIndices = rowIndices
            rowIndices = tmp

            theta = PI * (steps - row) / steps
            r = sin(theta).toFloat() * props.radius
            y = cos(theta).toFloat() * props.radius
            for (i in 0..(steps * 2)) {
                val phi = PI * i / steps
                val x = cos(-phi).toFloat() * r
                val z = sin(-phi).toFloat() * r
                val uv = props.texCoordGenerator(theta.toFloat(), phi.toFloat())
                rowIndices[i] = vertex(tmpPos.set(x, y, z).add(props.center),
                        tmpNrm.set(x, y, z).scale(1f / props.radius), uv)

                if (i > 0) {
                    geometry.addTriIndices(prevIndices[i - 1], rowIndices[i], rowIndices[i - 1])
                    geometry.addTriIndices(prevIndices[i - 1], prevIndices[i], rowIndices[i])
                }
            }
        }

        // top cap
        for (i in 1..(steps * 2)) {
            val uv = props.texCoordGenerator(0f, (PI * i / steps).toFloat())
            val iCenter = vertex(tmpPos.set(props.center.x, props.center.y + props.radius, props.center.z),
                    Vec3f.Y_AXIS, uv)
            geometry.addTriIndices(iCenter, rowIndices[i - 1], rowIndices[i])
        }
    }

    inline fun icoSphere(props: SphereProps.() -> Unit) {
        sphereProps.defaults().props()
        icoSphere(sphereProps)
    }

    fun icoSphere(props: SphereProps) {
        // https://schneide.blog/2016/07/15/generating-an-icosphere-in-c/

        val x = 0.525731112f
        val z = 0.850650808f
        val n = 0f

        val verts = mutableListOf(
                Vec3f(-x, n, z), Vec3f(x, n, z), Vec3f(-x, n, -z), Vec3f(x, n, -z),
                Vec3f(n, z, x), Vec3f(n, z, -x), Vec3f(n, -z, x), Vec3f(n, -z, -x),
                Vec3f(z, x, n), Vec3f(-z, x, n), Vec3f(z, -x, n), Vec3f(-z, -x, n)
        )
        var faces = mutableListOf(
                4,0,1, 9,0,4, 5,9,4, 5,4,8, 8,4,1,
                10,8,1, 3,8,10, 3,5,8, 2,5,3, 7,2,3,
                10,7,3, 6,7,10, 11,7,6, 0,11,6, 1,0,6,
                1,6,10, 0,9,11, 11,9,2, 2,9,5, 2,7,11
        )

        val midVerts = mutableMapOf<Long, Int>()
        fun getMidVertex(fromIdx: Int, toIdx: Int): Int {
            val key = (min(fromIdx, toIdx).toLong() shl 32) + max(fromIdx, toIdx)
            return midVerts.getOrPut(key) {
                val insertIdx = verts.size
                verts += MutableVec3f(verts[fromIdx]).add(verts[toIdx]).norm()
                insertIdx
            }
        }

        // subdivide ico-sphere
        val its = if (props.steps <= 8) { props.steps } else {
            logW { "clamping too large number of iterations for ico-sphere (${props.steps}) to 8" }
            8
        }
        for (i in 0 until its) {
            val newFaces = mutableListOf<Int>()
            for (j in faces.indices step 3) {
                val v1 = faces[j]
                val v2 = faces[j + 1]
                val v3 = faces[j + 2]

                // subdivide edges
                val a = getMidVertex(v1, v2)
                val b = getMidVertex(v2, v3)
                val c = getMidVertex(v3, v1)

                newFaces.addAll(listOf(v1, a, c))
                newFaces.addAll(listOf(v2, b, a))
                newFaces.addAll(listOf(v3, c, b))
                newFaces.addAll(listOf(a, b, c))
            }
            faces = newFaces
        }

        val pif = PI.toFloat()
        val uvVerts = verts.map { v -> v to Vec2f((atan2(v.x, v.z) + pif) / (2 * pif), acos(v.y) / pif) }.toMutableList()

        // duplicate vertices at texture border
        for (i in faces.indices step 3) {
            // check if triangle stretches across texture border and duplicate vertex with adjusted uv if it does
            for (j in 0..2) {
                val i1 = i + j
                val i2 = i + (j+1) % 3
                val i3 = i + (j+2) % 3

                val u1 = uvVerts[faces[i1]].second.x
                val u2 = uvVerts[faces[i2]].second.x
                val u3 = uvVerts[faces[i3]].second.x

                if (u1 - u2 > 0.5f && u1 - u3 > 0.5f) {
                    val dv1 = Vec3f(uvVerts[faces[i1]].first)
                    val du1 = MutableVec2f(uvVerts[faces[i1]].second).apply { this.x -= 1f }
                    faces[i1] = uvVerts.size
                    uvVerts += dv1 to du1
                } else if (u2 - u1 > 0.5f && u3 - u1 > 0.5f) {
                    val dv1 = Vec3f(uvVerts[faces[i1]].first)
                    val du1 = MutableVec2f(uvVerts[faces[i1]].second).apply { this.x += 1f }
                    faces[i1] = uvVerts.size
                    uvVerts += dv1 to du1
                }
            }
        }

        // insert geometry
        val nrm = MutableVec3f()
        val pos = MutableVec3f()
        val i0 = geometry.numIndices
        for (v in uvVerts) {
            nrm.set(v.first).norm()
            pos.set(nrm).scale(props.radius).add(props.center)
            val uv = props.texCoordGenerator(v.second.y * pif, v.second.x * 2 * pif)
            vertex(pos, nrm, uv)
        }
        for (i in faces.indices step 3) {
            geometry.addTriIndices(faces[i0 + i], faces[i0 + 1 + i], faces[i0 + 2 + i])
        }
    }

    inline fun rect(props: RectProps.() -> Unit) {
        rectProps.defaults().props()
        rect(rectProps)
    }

    fun rect(props: RectProps) {
        props.fixNegativeSize()

        if (props.cornerRadius == 0f) {
            val i0 = vertex(tmpPos.set(props.origin.x, props.origin.y, props.origin.z),
                    Vec3f.Z_AXIS, props.texCoordLowerLeft)
            val i1 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y, props.origin.z),
                    Vec3f.Z_AXIS, props.texCoordLowerRight)
            val i2 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y + props.size.y, props.origin.z),
                    Vec3f.Z_AXIS, props.texCoordUpperRight)
            val i3 = vertex(tmpPos.set(props.origin.x, props.origin.y + props.size.y, props.origin.z),
                    Vec3f.Z_AXIS, props.texCoordUpperLeft)
            geometry.addTriIndices(i0, i1, i2)
            geometry.addTriIndices(i0, i2, i3)

        } else {
            val x = props.origin.x
            val y = props.origin.y
            val z = props.origin.z
            val w = props.size.x
            val h = props.size.y
            val xI = x + props.cornerRadius
            val yI = y + props.cornerRadius
            val wI = w - props.cornerRadius * 2
            val hI = h - props.cornerRadius * 2
            val nrm = Vec3f.Z_AXIS

            // compute tex coord insets, this only works for axis aligned tex coords...
            val uI = (props.texCoordUpperRight.x - props.texCoordUpperLeft.x) * props.cornerRadius / w
            val vI = (props.texCoordUpperRight.y - props.texCoordLowerRight.y) * props.cornerRadius / h

            if (hI > 0) {
                val i0 = vertex(tmpPos.set(x, yI, z), nrm, tmpUv.set(0f, vI).add(props.texCoordLowerLeft))
                val i1 = vertex(tmpPos.set(x + w, yI, z), nrm, tmpUv.set(0f, vI).add(props.texCoordLowerRight))
                val i2 = vertex(tmpPos.set(x + w, yI + hI, z), nrm, tmpUv.set(0f, -vI).add(props.texCoordUpperRight))
                val i3 = vertex(tmpPos.set(x, yI + hI, z), nrm, tmpUv.set(0f, -vI).add(props.texCoordUpperLeft))
                geometry.addTriIndices(i0, i1, i2)
                geometry.addTriIndices(i0, i2, i3)
            }

            if (wI > 0) {
                var i0 = vertex(tmpPos.set(xI, y, z), nrm, tmpUv.set(uI, 0f).add(props.texCoordLowerLeft))
                var i1 = vertex(tmpPos.set(xI + wI, y, z), nrm, tmpUv.set(-uI, 0f).add(props.texCoordLowerRight))
                var i2 = vertex(tmpPos.set(xI + wI, yI, z), nrm, tmpUv.set(-uI, vI).add(props.texCoordLowerRight))
                var i3 = vertex(tmpPos.set(xI, yI, z), nrm, tmpUv.set(uI, vI).add(props.texCoordLowerLeft))
                geometry.addTriIndices(i0, i1, i2)
                geometry.addTriIndices(i0, i2, i3)

                i0 = vertex(tmpPos.set(xI, yI + hI, z), nrm, tmpUv.set(uI, -vI).add(props.texCoordUpperLeft))
                i1 = vertex(tmpPos.set(xI + wI, yI + hI, z), nrm, tmpUv.set(-uI, -vI).add(props.texCoordUpperRight))
                i2 = vertex(tmpPos.set(xI + wI, y + h, z), nrm, tmpUv.set(-uI, 0f).add(props.texCoordUpperRight))
                i3 = vertex(tmpPos.set(xI, y + h, z), nrm, tmpUv.set(uI, 0f).add(props.texCoordUpperLeft))
                geometry.addTriIndices(i0, i1, i2)
                geometry.addTriIndices(i0, i2, i3)
            }

            circle {
                center.set(xI + wI, yI + hI, z)
                startDeg = 0f
                sweepDeg = 90f
                radius = props.cornerRadius
                steps = props.cornerSteps
                uvCenter.set(-uI, -vI).add(props.texCoordUpperRight)
                uvRadius = uI
            }
            circle {
                center.set(xI, yI + hI, z)
                startDeg = 90f
                sweepDeg = 90f
                radius = props.cornerRadius
                steps = props.cornerSteps
                uvCenter.set(uI, -vI).add(props.texCoordUpperLeft)
                uvRadius = uI
            }
            circle {
                center.set(xI, yI, z)
                startDeg = 180f
                sweepDeg = 90f
                radius = props.cornerRadius
                steps = props.cornerSteps
                uvCenter.set(uI, vI).add(props.texCoordLowerLeft)
                uvRadius = uI
            }
            circle {
                center.set(xI + wI, yI, z)
                startDeg = 270f
                sweepDeg = 90f
                radius = props.cornerRadius
                steps = props.cornerSteps
                uvCenter.set(-uI, vI).add(props.texCoordLowerRight)
                uvRadius = uI
            }
        }
    }

    fun line(pt1: Vec2f, pt2: Vec2f, width: Float) {
        line(pt1.x, pt1.y, pt2.x, pt2.y, width)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float) {
        var dx = x2 - x1
        var dy = y2 - y1
        var len = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        val addX = width * 0.25f * dx / len
        val addY = width * 0.25f * dy / len
        dx += addX + addX
        dy += addY + addY
        len += width * 0.5f

        val dxu = dx / len * width / 2
        val dyu = dy / len * width / 2

        val qx0 = x1 - addX + dyu
        val qy0 = y1 - addY - dxu

        val qx1 = x2 + addX + dyu
        val qy1 = y2 + addY - dxu

        val qx2 = x2 + addX - dyu
        val qy2 = y2 + addY + dxu

        val qx3 = x1 - addX - dyu
        val qy3 = y1 - addY + dxu

        val i0 = vertex(tmpPos.set(qx0, qy0, 0f), Vec3f.Z_AXIS)
        val i1 = vertex(tmpPos.set(qx1, qy1, 0f), Vec3f.Z_AXIS)
        val i2 = vertex(tmpPos.set(qx2, qy2, 0f), Vec3f.Z_AXIS)
        val i3 = vertex(tmpPos.set(qx3, qy3, 0f), Vec3f.Z_AXIS)
        geometry.addTriIndices(i0, i1, i2)
        geometry.addTriIndices(i0, i2, i3)
    }

    fun lineArc(centerX: Float, centerY: Float, radius: Float, startDeg: Float, sweepDeg: Float, width: Float, resolution: Float = 3f) {
        val steps = max(1, round(abs(sweepDeg) / resolution).toInt())
        val step = sweepDeg / steps

        val startRad = startDeg.toRad()
        val stepRad = step.toRad()

        for (i in 0 until steps) {
            val a0 = startRad + stepRad * i
            val a1 = a0 + stepRad
            val x0 = centerX + cos(a0) * radius
            val y0 = centerY + sin(a0) * radius
            val x1 = centerX + cos(a1) * radius
            val y1 = centerY + sin(a1) * radius
            line(x0, y0, x1, y1, width)
        }
    }

    inline fun cube(props: CubeProps.() -> Unit) {
        cubeProps.defaults().props()
        cube(cubeProps)
    }

    fun cube(props: CubeProps) {
        props.fixNegativeSize()

        // front
        withColor(props.frontColor) {
            val i0 = vertex(tmpPos.set(props.origin.x, props.origin.y, props.origin.z + props.size.z), Vec3f.Z_AXIS, Vec2f(0f, 1f))
            val i1 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y, props.origin.z + props.size.z), Vec3f.Z_AXIS, Vec2f(1f, 1f))
            val i2 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y + props.size.y, props.origin.z + props.size.z), Vec3f.Z_AXIS, Vec2f(1f, 0f))
            val i3 = vertex(tmpPos.set(props.origin.x, props.origin.y + props.size.y, props.origin.z + props.size.z), Vec3f.Z_AXIS, Vec2f(0f, 0f))
            geometry.addTriIndices(i0, i1, i2)
            geometry.addTriIndices(i0, i2, i3)
        }

        // right
        withColor(props.rightColor) {
            val i0 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y, props.origin.z), Vec3f.X_AXIS, Vec2f(1f, 1f))
            val i1 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y + props.size.y, props.origin.z), Vec3f.X_AXIS, Vec2f(1f, 0f))
            val i2 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y + props.size.y, props.origin.z + props.size.z), Vec3f.X_AXIS, Vec2f(0f, 0f))
            val i3 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y, props.origin.z + props.size.z), Vec3f.X_AXIS, Vec2f(0f, 1f))
            geometry.addTriIndices(i0, i1, i2)
            geometry.addTriIndices(i0, i2, i3)
        }

        // back
        withColor(props.backColor) {
            val i0 = vertex(tmpPos.set(props.origin.x, props.origin.y + props.size.y, props.origin.z), Vec3f.NEG_Z_AXIS, Vec2f(1f, 0f))
            val i1 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y + props.size.y, props.origin.z), Vec3f.NEG_Z_AXIS, Vec2f(0f, 0f))
            val i2 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y, props.origin.z), Vec3f.NEG_Z_AXIS, Vec2f(0f, 1f))
            val i3 = vertex(tmpPos.set(props.origin.x, props.origin.y, props.origin.z), Vec3f.NEG_Z_AXIS, Vec2f(1f, 1f))
            geometry.addTriIndices(i0, i1, i2)
            geometry.addTriIndices(i0, i2, i3)
        }

        // left
        withColor(props.leftColor) {
            val i0 = vertex(tmpPos.set(props.origin.x, props.origin.y, props.origin.z + props.size.z), Vec3f.NEG_X_AXIS, Vec2f(1f, 1f))
            val i1 = vertex(tmpPos.set(props.origin.x, props.origin.y + props.size.y, props.origin.z + props.size.z), Vec3f.NEG_X_AXIS, Vec2f(1f, 0f))
            val i2 = vertex(tmpPos.set(props.origin.x, props.origin.y + props.size.y, props.origin.z), Vec3f.NEG_X_AXIS, Vec2f(0f, 0f))
            val i3 = vertex(tmpPos.set(props.origin.x, props.origin.y, props.origin.z), Vec3f.NEG_X_AXIS, Vec2f(0f, 1f))
            geometry.addTriIndices(i0, i1, i2)
            geometry.addTriIndices(i0, i2, i3)
        }

        // top
        withColor(props.topColor) {
            val i0 = vertex(tmpPos.set(props.origin.x, props.origin.y + props.size.y, props.origin.z + props.size.z), Vec3f.Y_AXIS, Vec2f(0f, 1f))
            val i1 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y + props.size.y, props.origin.z + props.size.z), Vec3f.Y_AXIS, Vec2f(1f, 1f))
            val i2 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y + props.size.y, props.origin.z), Vec3f.Y_AXIS, Vec2f(1f, 0f))
            val i3 = vertex(tmpPos.set(props.origin.x, props.origin.y + props.size.y, props.origin.z), Vec3f.Y_AXIS, Vec2f(0f, 0f))
            geometry.addTriIndices(i0, i1, i2)
            geometry.addTriIndices(i0, i2, i3)
        }

        // bottom
        withColor(props.bottomColor) {
            val i0 = vertex(tmpPos.set(props.origin.x, props.origin.y, props.origin.z), Vec3f.NEG_Y_AXIS, Vec2f(0f, 1f))
            val i1 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y, props.origin.z), Vec3f.NEG_Y_AXIS, Vec2f(1f, 1f))
            val i2 = vertex(tmpPos.set(props.origin.x + props.size.x, props.origin.y, props.origin.z + props.size.z), Vec3f.NEG_Y_AXIS, Vec2f(1f, 0f))
            val i3 = vertex(tmpPos.set(props.origin.x, props.origin.y, props.origin.z + props.size.z), Vec3f.NEG_Y_AXIS, Vec2f(0f, 0f))
            geometry.addTriIndices(i0, i1, i2)
            geometry.addTriIndices(i0, i2, i3)
        }
    }

    inline fun cylinder(props: CylinderProps.() -> Unit) {
        cylinderProps.defaults().props()
        cylinder(cylinderProps)
    }

    fun cylinder(props: CylinderProps) {
        props.fixNegativeSize()

        // bottom
        if (props.bottomFill) {
            withTransform {
                translate(props.origin)
                rotate(90f, Vec3f.X_AXIS)
                circle {
                    steps = props.steps
                    radius = props.bottomRadius
                }
            }
        }
        // top
        if (props.topFill) {
            withTransform {
                translate(props.origin.x, props.origin.y + props.height, props.origin.z)
                rotate(-90f, Vec3f.X_AXIS)
                circle {
                    steps = props.steps
                    radius = props.topRadius
                }
            }
        }

        val dr = props.bottomRadius - props.topRadius
        val nrmAng = 90f - acos(dr / sqrt(dr * dr + props.height * props.height)).toDeg()
        var i0 = 0
        var i1 = 0
        for (i in 0..props.steps) {
            val c = cos(i * PI * 2 / props.steps).toFloat()
            val s = sin(i * PI * 2 / props.steps).toFloat()

            val px2 = props.origin.x + props.bottomRadius * c
            val pz2 = props.origin.z + props.bottomRadius * s
            val px3 = props.origin.x + props.topRadius * c
            val pz3 = props.origin.z + props.topRadius * s

            tmpNrm.set(c, 0f, s).rotate(nrmAng, s, 0f, c)
            val i2 = vertex(tmpPos.set(px2, props.origin.y, pz2), tmpNrm)
            val i3 = vertex(tmpPos.set(px3, props.origin.y + props.height, pz3), tmpNrm)

            if (i > 0) {
                geometry.addTriIndices(i0, i1, i2)
                geometry.addTriIndices(i1, i3, i2)
            }
            i0 = i2
            i1 = i3
        }
    }

    inline fun grid(props: GridProps.() -> Unit) {
        gridProps.defaults().props()
        grid(gridProps)
    }

    fun grid(props: GridProps) {
        val bx = -props.sizeX / 2
        val by = -props.sizeY / 2
        val sx = props.sizeX / props.stepsX
        val sy = props.sizeY / props.stepsY
        val nx = props.stepsX + 1
        props.xDir.cross(props.yDir, tmpNrm).norm()

        for (y in 0 .. props.stepsY) {
            for (x in 0 .. props.stepsX) {
                val px = bx + x * sx
                val py = by + y * sy
                val h = props.heightFun(x, y)
                tmpPos.set(props.center)
                tmpPos.x += props.xDir.x * px + props.yDir.x * py + tmpNrm.x * h
                tmpPos.y += props.xDir.y * px + props.yDir.y * py + tmpNrm.y * h
                tmpPos.z += props.xDir.z * px + props.yDir.z * py + tmpNrm.z * h

                val idx = vertex(tmpPos, Vec3f.ZERO)
                if (x > 0 && y > 0) {
                    if (x % 2 == y % 2) {
                        geometry.addTriIndices(idx - nx - 1, idx, idx - 1)
                        geometry.addTriIndices(idx - nx, idx, idx - nx - 1)
                    } else {
                        geometry.addTriIndices(idx - nx, idx, idx - 1)
                        geometry.addTriIndices(idx - nx, idx - 1, idx - nx - 1)
                    }
                }
            }
        }

        val iTri = geometry.numIndices - props.stepsX * props.stepsY * 6
        val e1 = MutableVec3f()
        val e2 = MutableVec3f()
        val v1 = geometry[0]
        val v2 = geometry[0]
        val v3 = geometry[0]
        for (i in iTri until geometry.numIndices step 3) {
            v1.index = geometry.indices[i]
            v2.index = geometry.indices[i+1]
            v3.index = geometry.indices[i+2]
            v2.position.subtract(v1.position, e1).norm()
            v3.position.subtract(v1.position, e2).norm()
            e1.cross(e2, tmpNrm).norm()
            v1.normal.add(tmpNrm)
            v2.normal.add(tmpNrm)
            v3.normal.add(tmpNrm)
        }

        val iVert = geometry.numVertices - (props.stepsX + 1) * (props.stepsY + 1)
        for (i in iVert until geometry.numVertices) {
            v1.index = i
            v1.normal.norm()
        }
    }

    inline fun text(font: Font, fontSizeUnits: Float = 0f, block: TextProps.() -> Unit) {
        val props = textProps ?: TextProps(font).apply { textProps = this}
        props.defaults()
        props.font = font
        props.block()
        text(props, fontSizeUnits)
    }

    fun text(props: TextProps, fontSizeUnits: Float = 0f) {
        withTransform {
            if (fontSizeUnits != 0f) {
                val s = fontSizeUnits / props.font.charMap.fontProps.sizePts
                scale(s, s, s)
            }
            translate(props.origin)

            var advanced = 0f
            for (c in props.text) {
                if (c == '\n') {
                    translate(0f, -props.font.lineSpace, 0f)
                    advanced = 0f
                }

                val metrics = props.font.charMap[c]
                if (metrics != null) {
                    rect {
                        origin.set(advanced - metrics.xOffset, metrics.yBaseline - metrics.height, 0f)
                        size.set(metrics.width, metrics.height)

                        texCoordUpperLeft.set(metrics.uvMin)
                        texCoordUpperRight.set(metrics.uvMax.x, metrics.uvMin.y)
                        texCoordLowerLeft.set(metrics.uvMin.x, metrics.uvMax.y)
                        texCoordLowerRight.set(metrics.uvMax)
                    }
                    advanced += metrics.advance
                }
            }
        }
    }
}

class CircleProps {
    var radius = 1f
    var steps = 20
    val center = MutableVec3f()
    var startDeg = 0f
    var sweepDeg = 360f

    val uvCenter = MutableVec2f()
    var uvRadius = 0f

    fun defaults(): CircleProps {
        radius = 1f
        steps = 20
        center.set(Vec3f.ZERO)
        startDeg = 0f
        sweepDeg = 360f
        zeroTexCoords()
        return this
    }

    fun zeroTexCoords() {
        uvCenter.set(Vec2f.ZERO)
        uvRadius = 0f
    }

    fun fullTexCoords() {
        uvCenter.set(0.5f, 0.5f)
        uvRadius = 0.5f
    }
}

class GridProps {
    val center = MutableVec3f()
    val xDir = MutableVec3f()
    val yDir = MutableVec3f()
    var sizeX = 0f
    var sizeY = 0f
    var stepsX = 0
    var stepsY = 0
    var heightFun: (Int, Int) -> Float = ZERO_HEIGHT

    init {
        defaults()
    }

    fun defaults(): GridProps {
        center.set(Vec3f.ZERO)
        xDir.set(Vec3f.X_AXIS)
        yDir.set(Vec3f.NEG_Z_AXIS)
        sizeX = 10f
        sizeY = 10f
        stepsX = 10
        stepsY = 10
        return this
    }

    companion object {
        val ZERO_HEIGHT: (Int, Int) -> Float = { _, _ -> 0f }
    }
}

class SphereProps {
    var radius = 1f
    var steps = 20
    val center = MutableVec3f()

    private val uv = MutableVec2f()

    var texCoordGenerator: (Float, Float) -> Vec2f = { t, p -> defaultTexCoordGenerator(t, p) }

    private fun defaultTexCoordGenerator(theta: Float, phi: Float): Vec2f {
        return uv.set(phi / (PI.toFloat() * 2f), theta / PI.toFloat())
    }

    fun defaults(): SphereProps {
        radius = 1f
        steps = 20
        center.set(Vec3f.ZERO)
        texCoordGenerator = { t, p -> defaultTexCoordGenerator(t, p) }
        return this
    }
}

class RectProps {
    var cornerRadius = 0f
    var cornerSteps = 8
    val origin = MutableVec3f()
    val size = MutableVec2f()

    var width: Float
        get() = size.x
        set(value) { size.x = value }
    var height: Float
        get() = size.y
        set(value) { size.y = value }

    val texCoordUpperLeft = MutableVec2f()
    val texCoordUpperRight = MutableVec2f()
    val texCoordLowerLeft = MutableVec2f()
    val texCoordLowerRight = MutableVec2f()

    init {
        generateTexCoords(1f)
    }

    fun fixNegativeSize() {
        if (size.x < 0) {
            origin.x += size.x
            size.x = -size.x
        }
        if (size.y < 0) {
            origin.y += size.y
            size.y = -size.y
        }
    }

    fun zeroTexCoords() = generateTexCoords(0f)

    fun generateTexCoords(scale: Float = 1f) {
        texCoordUpperLeft.set(0f, 0f)
        texCoordUpperRight.set(scale, 0f)
        texCoordLowerLeft.set(0f, scale)
        texCoordLowerRight.set(scale, scale)
    }

    fun defaults(): RectProps {
        cornerRadius = 0f
        cornerSteps = 8
        origin.set(Vec3f.ZERO)
        size.set(1f, 1f)
        generateTexCoords(1f)
        return this
    }
}

class CubeProps {
    val origin = MutableVec3f()
    val size = MutableVec3f()

    var width: Float
        get() = size.x
        set(value) { size.x = value }
    var height: Float
        get() = size.y
        set(value) { size.y = value }
    var depth: Float
        get() = size.z
        set(value) { size.z = value }

    var topColor: Color? = null
    var bottomColor: Color? = null
    var leftColor: Color? = null
    var rightColor: Color? = null
    var frontColor: Color? = null
    var backColor: Color? = null

    fun fixNegativeSize() {
        if (size.x < 0) {
            origin.x += size.x
            size.x = -size.x
        }
        if (size.y < 0) {
            origin.y += size.y
            size.y = -size.y
        }
        if (size.z < 0) {
            origin.z += size.z
            size.z = -size.z
        }
    }

    fun centered() {
        origin.x -= size.x / 2f
        origin.y -= size.y / 2f
        origin.z -= size.z / 2f
    }

    fun colored(linearSpace: Boolean = true) {
        if (linearSpace) {
            frontColor = Color.MD_RED.toLinear()
            rightColor = Color.MD_AMBER.toLinear()
            backColor = Color.MD_INDIGO.toLinear()
            leftColor = Color.MD_CYAN.toLinear()
            topColor = Color.MD_PURPLE.toLinear()
            bottomColor = Color.MD_GREEN.toLinear()
        } else {
            frontColor = Color.MD_RED
            rightColor = Color.MD_AMBER
            backColor = Color.MD_INDIGO
            leftColor = Color.MD_CYAN
            topColor = Color.MD_PURPLE
            bottomColor = Color.MD_GREEN
        }
    }

    fun defaults(): CubeProps {
        size.x = 1f
        size.y = 1f
        size.z = 1f
        origin.set(Vec3f.ZERO)

        topColor = null
        bottomColor = null
        leftColor = null
        rightColor = null
        frontColor = null
        backColor = null

        return this
    }
}

class CylinderProps {
    var bottomRadius = 1f
    var topRadius = 1f
    var steps = 20
    var height = 1f
    var topFill = true
    var bottomFill = true
    val origin = MutableVec3f()

    fun defaults(): CylinderProps {
        bottomRadius = 1f
        topRadius = 1f
        steps = 20
        height = 1f
        topFill = true
        bottomFill = true
        origin.set(Vec3f.ZERO)
        return this
    }

    fun fixNegativeSize() {
        if (height < 0) {
            origin.y += height
            height = -height
        }
    }
}

class TextProps(var font: Font) {
    var text = ""
    val origin = MutableVec3f()

    fun defaults(): TextProps {
        text = ""
        origin.set(Vec3f.ZERO)
        return this
    }
}