package ecs;

import ecs.util.Container;


/**
 * @author Frederik Dahl
 * 22/08/2021
 */


public class EntityManager {

    private final ComponentManager componentManager;
    private final SystemManager systemManager;

    private final Container<Entity> entities;
    private final Container<Entity> dirty;
    private final EntityPool pool;


    protected EntityManager(ECS ecs, int initialCapacity, int maxPoolSize) {
        if (ecs == null) throw new IllegalStateException("");
        componentManager = ecs.componentManager;
        systemManager = ecs.systemManager;
        entities = new Container<>(initialCapacity);
        dirty = new Container<>(initialCapacity);
        pool = new EntityPool(initialCapacity,maxPoolSize);
        pool.fill(initialCapacity/2);
    }

    public Entity create() {
        Entity e = pool.obtain();
        entities.set(e,e.id);
        return e;
    }

    /**
     * Used to delete entities (return entities to pool).
     * This removes all components from the entity, and marks it dirty.
     * Dirty entities get "cleaned" after each EntitySystems' process-loop.
     * Dirty entities without components gets deleted.
     *
     * Note: If you remove an entity then add components to it within
     * the execution of the SAME EntitySystem, the entity will not get deleted.
     *
     * @param e the entity to remove
     */
    public void remove(Entity e) {
        if (e.hasAnyComponent())
            componentManager.removeAll(e);
        refresh(e);
    }

    public void addComponents(Entity e, Component... components) {
        boolean refresh = false;
        for (Component c : components) {
            if (!componentManager.addComponent(e, c))
                refresh = true;
        }if (refresh) refresh(e);
    }

    public void addComponent(Entity e, Component c) {
        if (!componentManager.addComponent(e,c)) // if !replaced
            refresh(e);
    }

    public void removeComponent(Entity e, ComponentType t) {
        if (componentManager.removeComponent(e,t))
            refresh(e);
    }

    public void removeComponent(Entity e, Component c) {
        if (componentManager.removeComponent(e,c))
            refresh(e);
    }

    public void disable(Entity e) {
        if (e.enabled) refresh(e);
        e.enabled = false;
    }

    public void enable(Entity e) {
        if (!e.enabled) refresh(e);
        e.enabled = true;
    }

    /**
     * Adds the entity e to the dirty container.
     * Dirty entities gets "cleaned" (revalidated) after each EntitySystem loop, then removed from dirty.
     * Refreshing an entity without components will delete (free) the entity.
     *
     * @param e the entity to refresh
     */
    public void refresh(Entity e) {
        if (e.dirty) return;
        dirty.push(e);
        e.dirty = true;
    }

    public int entities() {
        return entities.itemCount();
    }

    public long entitiesCreated() {
        return pool.obtained();
    }

    public int entitiesDestroyed() {
        return pool.discarded();
    }

    public int entitiesInMemory() {
        return pool.objectsInMemory();
    }

    /**
     * "Cleans" entities marked as dirty.
     * (Adding/removing components to/from an entity marks it as dirty)
     * This gets called at the end of each EntitySystems' process-loop.
     * Any dirty entities will get revalidated by each EntitySystem registered by the ECS.
     *
     * Note: Entities marked as dirty without components, will be deleted after clean.
     * Deleting an entity is equivalent of removing all it's components and vice-versa.
     */
    protected void clean() {
        if (dirty.notEmpty()) {
            Container<EntitySystem> systems = systemManager.systems;
            int systemCount = systems.itemCount();
            int dirtyCount = dirty.itemCount();
            for (int i = 0; i < dirtyCount; i++) {
                Entity entity = dirty.get(i);
                for (int j = 0; j < systemCount; j++)
                    systems.get(j).revalidate(entity);
                entity.dirty = false;
                if (!entity.hasAnyComponent())
                    delete(entity);
            }dirty.clear();
        }
    }

    private void delete(Entity e) {
        entities.remove(e.id);
        pool.free(e);
    }

}
