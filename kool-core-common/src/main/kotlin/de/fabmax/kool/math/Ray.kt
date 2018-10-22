package de.fabmax.kool.math

import de.fabmax.kool.scene.Node

/**
 * @author fabmax
 */

class Ray {
    val origin = MutableVec3f()
    val direction = MutableVec3f()

    fun set(other: Ray) {
        origin.set(other.origin)
        direction.set(other.direction)
    }

    fun setFromLookAt(origin: Vec3f, lookAt: Vec3f) {
        this.origin.set(origin)
        direction.set(lookAt).subtract(origin).norm()
    }

    fun distanceToPoint(point: Vec3f): Float = point.distanceToRay(origin, direction)

    fun sqrDistanceToPoint(point: Vec3f): Float = point.sqrDistanceToRay(origin, direction)

    fun sqrDistanceToPoint(x: Float, y: Float, z: Float) = sqrDistancePointToRay(x, y, z, origin, direction)
}

class RayTest {

    val ray = Ray()

    private val intHitPosition = MutableVec3f()
    private val intHitPositionLocal = MutableVec3f()

    val hitPosition: Vec3f get() = intHitPosition
    val hitPositionLocal : Vec3f get() = intHitPositionLocal
    var hitNode: Node? = null
        private set
    var hitDistanceSqr = Float.MAX_VALUE
        private set
    val isHit: Boolean
        get() = hitDistanceSqr < Float.MAX_VALUE

    fun clear() {
        intHitPosition.set(Vec3f.ZERO)
        intHitPositionLocal.set(Vec3f.ZERO)
        hitNode = null
        hitDistanceSqr = Float.MAX_VALUE
    }

    fun setHit(node: Node, distance: Float) {
        intHitPosition.set(ray.direction).scale(distance).add(ray.origin)
        setHit(node, intHitPosition)
    }

    fun setHit(node: Node, position: Vec3f) {
        intHitPosition.set(position)
        intHitPositionLocal.set(position)
        hitNode = node
        hitDistanceSqr = hitPosition.sqrDistance(ray.origin)
    }

    fun transformBy(matrix: Mat4f) {
        matrix.transform(ray.origin)
        matrix.transform(ray.direction, 0f)
        ray.direction.norm()
        if (isHit) {
            matrix.transform(intHitPosition)
            hitDistanceSqr = hitPosition.sqrDistance(ray.origin)
        }
    }
}
