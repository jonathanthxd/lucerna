package us.z1x.lucerna.shadows.frustum;

import net.minecraft.util.math.Box;

public class BoxCuller {
    private final double maxDistance;

    private double minAllowedX;
    private double maxAllowedX;
    private double minAllowedY;
    private double maxAllowedY;
    private double minAllowedZ;
    private double maxAllowedZ;

    public BoxCuller(double maxDistance) {
        this.maxDistance = maxDistance;
    }

    public void setPosition(double cameraX, double cameraY, double cameraZ) {
        this.minAllowedX = cameraX - maxDistance;
        this.maxAllowedX = cameraX + maxDistance;
        this.minAllowedY = cameraY - maxDistance;
        this.maxAllowedY = cameraY + maxDistance;
        this.minAllowedZ = cameraZ - maxDistance;
        this.maxAllowedZ = cameraZ + maxDistance;
    }

    public boolean isCulled(Box aabb) {
        return isCulled(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    public boolean isCulled(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (maxX < this.minAllowedX || minX > this.maxAllowedX) {
            return true;
        }

        if (maxY < this.minAllowedY || minY > this.maxAllowedY) {
            return true;
        }

        return maxZ < this.minAllowedZ || minZ > this.maxAllowedZ;
    }

    public boolean isCulledCeleritas(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (maxX < -this.maxDistance || minX > this.maxDistance) {
            return true;
        }

        if (maxY < -this.maxDistance || minY > this.maxDistance) {
            return true;
        }

        return maxZ < -this.maxDistance || minZ > this.maxDistance;
    }

    // camera relative, true only when the whole box is inside the bounds on every axis so no sub-box can be culled
    public boolean isFullyInsideCeleritas(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return minX >= -this.maxDistance && maxX <= this.maxDistance
            && minY >= -this.maxDistance && maxY <= this.maxDistance
            && minZ >= -this.maxDistance && maxZ <= this.maxDistance;
    }

    @Override
    public String toString() {
        return "Box Culling active; max distance " + maxDistance;
    }
}
