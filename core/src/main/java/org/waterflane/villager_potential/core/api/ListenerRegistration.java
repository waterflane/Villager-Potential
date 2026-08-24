package org.waterflane.villager_potential.core.api;

/** A removable integration listener registration. Closing twice is harmless. */
@FunctionalInterface
public interface ListenerRegistration extends AutoCloseable {
    @Override
    void close();
}
