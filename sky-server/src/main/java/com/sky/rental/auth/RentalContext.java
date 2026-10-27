package com.sky.rental.auth;

/** Request-scoped identity, independent of legacy employee context. */
public final class RentalContext {
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();
    private RentalContext() { }
    public static void set(Long id, String role, String name, String username) { CURRENT.set(new Identity(id, role, name, username)); }
    public static Long getUserId() { return CURRENT.get() == null ? null : CURRENT.get().id; }
    public static String getRole() { return CURRENT.get() == null ? null : CURRENT.get().role; }
    public static String getName() { return CURRENT.get() == null ? null : CURRENT.get().name; }
    public static String getUsername() { return CURRENT.get() == null ? null : CURRENT.get().username; }
    public static void clear() { CURRENT.remove(); }
    private static final class Identity {
        final Long id; final String role; final String name; final String username;
        Identity(Long id, String role, String name, String username) { this.id=id; this.role=role; this.name=name; this.username=username; }
    }
}
