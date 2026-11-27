package pt.ipt.technochained.network;

import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;

public final class RmiUtils {

    public static final String DEFAULT_SERVICE_NAME = "TechnoChainedNode";

    private RmiUtils() {}

    public static String getRemoteName(String host, int port, String objectName) {
        return String.format("//%s:%d/%s", host, port, objectName);
    }

    public static String getRemoteName(int port, String objectName) {
        return getRemoteName("localhost", port, objectName);
    }

    public static String startRemoteObject(Remote remote, String host, int port, String objectName) throws Exception {
        System.setProperty("java.rmi.server.hostname", host);
        try {
            LocateRegistry.createRegistry(port);
        } catch (ExportException ignored) {
            // O registry neste porto já estava criado nesta JVM
        }
        String address = getRemoteName(host, port, objectName);
        Naming.rebind(address, remote);
        return address;
    }

    public static Remote getRemote(String fullAddress) throws Exception {
        return Naming.lookup(fullAddress);
    }

    public static Remote getRemote(String host, int port, String objectName) throws Exception {
        return getRemote(getRemoteName(host, port, objectName));
    }

    public static void stopRemoteObject(Remote remote, String fullAddress) {
        try {
            Naming.unbind(fullAddress);
        } catch (Exception ignored) {}
        try {
            UnicastRemoteObject.unexportObject(remote, true);
        } catch (Exception ignored) {}
    }
}
