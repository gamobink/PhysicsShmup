package com.mygdx.game.actors;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.steer.SteeringAcceleration;
import com.badlogic.gdx.ai.steer.SteeringBehavior;
import com.badlogic.gdx.ai.steer.behaviors.Arrive;
import com.badlogic.gdx.ai.steer.behaviors.Flee;
import com.badlogic.gdx.ai.steer.behaviors.Seek;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.mygdx.game.components.physics.BodyComponent;
import com.mygdx.game.components.physics.PositionComponent;
import com.mygdx.game.components.physics.VelocityComponent;
import com.mygdx.managers.GameInput;
import com.mygdx.managers.PhysicsManager;

public class TestEnemySteering implements Steerable<Vector2>, Updateable {
    private ComponentMapper<PositionComponent> positionMap  = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<BodyComponent>     bodyMap      = ComponentMapper.getFor(BodyComponent.class);
    private ComponentMapper<VelocityComponent> velocityMap  = ComponentMapper.getFor(VelocityComponent.class);

    private static final SteeringAcceleration<Vector2> steeringOutput =
            new SteeringAcceleration<Vector2>(new Vector2(10, 1));

    private Vector2 position;
    private Vector2 linearVelocity;
    private float orientation;
    private float angularVelocity;

    private float maxLinearSpeed;
    private float maxLinearAcceleration;
    private float maxAngularSpeed;
    private float maxAngularAcceleration;

    private boolean independentFacing;
    private BodyComponent bodyCom;
    private Entity entity;
    private Steerable<Vector2> target;

    private SteeringBehavior<Vector2> steeringBehavior;

    public TestEnemySteering(Vector2 position) {
        this.position = position;
    }

    public TestEnemySteering(Entity entity, Steerable<Vector2> target) {
        independentFacing = true;
        maxLinearSpeed = 10f;
        maxLinearAcceleration = 1f;
        maxAngularSpeed = 3;
        maxAngularAcceleration = 3;
        this.target = target;

        position        = new Vector2(positionMap.get(entity).x, positionMap.get(entity).y);
        linearVelocity  = new Vector2(velocityMap.get(entity).x, velocityMap.get(entity).y);
        bodyCom         = bodyMap.get(entity);
        steeringBehavior = new Arrive<>(this, target).setDecelerationRadius(100).setArrivalTolerance(30);
    }

    public void setSteeringBehavior(Class<?> behaviorType) {
        if (behaviorType == Arrive.class) {
            steeringBehavior = new Arrive<>(this, target).setDecelerationRadius(100).setArrivalTolerance(30);
        } else if (behaviorType == Flee.class) {
            steeringBehavior = new Flee<>(this, target);
        }
    }

    // Actual implementation depends on your coordinate system.
    // Here we assume the y-axis is pointing upwards.
    @Override
    public float vectorToAngle (Vector2 vector) {
        return (float)Math.atan2(-vector.x, vector.y);
    }

    // Actual implementation depends on your coordinate system.
    // Here we assume the y-axis is pointing upwards.
    @Override
    public Vector2 angleToVector (Vector2 outVector, float angle) {
        outVector.x = -(float)Math.sin(angle);
        outVector.y = (float)Math.cos(angle);
        return outVector;
    }

    @Override
    public Location<Vector2> newLocation() {
        return null;
    }

    public static float calculateOrientationFromLinearVelocity (Steerable<Vector2> character) {
        // If we haven't got any velocity, then we can do nothing.
        if (character.getLinearVelocity().isZero(MathUtils.FLOAT_ROUNDING_ERROR))
            return character.getOrientation();

        return character.vectorToAngle(character.getLinearVelocity());
    }

    public void update (float delta) {
        if (steeringBehavior != null) {
            // Calculate steering acceleration
            steeringBehavior.calculateSteering(steeringOutput);

            // Apply steering acceleration to move this agent
            applySteering(steeringOutput, 1f);
        }
    }

    private void applySteering (SteeringAcceleration<Vector2> steering, float time) {
        this.linearVelocity.mulAdd(steering.linear, time).limit(this.getMaxLinearSpeed());

        Body body = bodyCom.body;
        linearVelocity = body.getLinearVelocity();
        float   velocityX = linearVelocity.x;

        float desiredVelocity = Math.max(velocityX - maxLinearSpeed,
                Math.min(Math.signum(steering.linear.x) * maxLinearAcceleration, velocityX + maxLinearSpeed));

        desiredVelocity = Math.min(desiredVelocity, maxLinearSpeed);

        float velocityChange = desiredVelocity - velocityX;
        float impulse = body.getMass() * velocityChange;
        body.applyLinearImpulse(impulse, 0, body.getWorldCenter().x, body.getWorldCenter().y, true);

        position.x = body.getPosition().x * PhysicsManager.METERS_TO_PIXELS;
        position.y = body.getPosition().y * PhysicsManager.METERS_TO_PIXELS;

        // Update orientation and angular velocity
        if (independentFacing) {
            this.orientation += angularVelocity * time;
            this.angularVelocity += steering.angular * time;
        } else {
            // For non-independent facing we have to align orientation to linear velocity
            float newOrientation = calculateOrientationFromLinearVelocity(this);
            if (newOrientation != this.orientation) {
                this.angularVelocity = (newOrientation - this.orientation) * time;
                this.orientation = newOrientation;
            }
        }
    }

    @Override
    public Vector2 getPosition() {
        return position;
    }

    @Override
    public float getOrientation() {
        return orientation;
    }

    @Override
    public void setOrientation(float orientation) {

    }

    @Override
    public Vector2 getLinearVelocity() {
        return linearVelocity;
    }

    @Override
    public float getBoundingRadius() {
        return 0;
    }

    @Override
    public float getAngularVelocity() {
        return 0;
    }

    @Override
    public boolean isTagged() {
        return false;
    }

    @Override
    public void setTagged(boolean tagged) {
        // ignoring this
    }

    @Override
    public float getZeroLinearSpeedThreshold() {
        return 0;
    }

    @Override
    public void setZeroLinearSpeedThreshold(float value) {

    }

    @Override
    public float getMaxLinearSpeed() {
        return maxLinearSpeed;
    }

    @Override
    public void setMaxLinearSpeed(float maxLinearSpeed) {
        this.maxLinearSpeed = maxLinearSpeed;
    }

    @Override
    public float getMaxLinearAcceleration() {
        return maxLinearAcceleration;
    }

    @Override
    public void setMaxLinearAcceleration(float maxLinearAcceleration) {
        this.maxLinearAcceleration = maxLinearAcceleration;
    }

    @Override
    public float getMaxAngularSpeed() {
        return maxAngularSpeed;
    }

    @Override
    public void setMaxAngularSpeed(float maxAngularSpeed) {
        this.maxAngularSpeed = maxAngularSpeed;
    }

    @Override
    public float getMaxAngularAcceleration() {
        return maxAngularAcceleration;
    }

    @Override
    public void setMaxAngularAcceleration(float maxAngularAcceleration) {
        this.maxAngularAcceleration = maxAngularAcceleration;
    }
}
