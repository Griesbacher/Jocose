package org.griesbacher.jocose;

import java.util.Arrays;

public class ConsulService {
    String Name;
    Check Check;
    String[] Tags;
    String Port;
    String EnableTagOverride;
    String Address;
    String ID;

    @Override
    public String toString() {
        return "ConsulService{" +
                "Name='" + Name + '\'' +
                ", Check=" + Check +
                ", Tags=" + Arrays.toString(Tags) +
                ", Port='" + Port + '\'' +
                ", EnableTagOverride='" + EnableTagOverride + '\'' +
                ", Address='" + Address + '\'' +
                ", ID='" + ID + '\'' +
                '}';
    }

    public class Check {
        String Script;
        String Interval;
        String HTTP;
        String DeregisterCriticalServiceAfter;
        String TTL;

        @Override
        public String toString() {
            return "Check{" +
                    "Script='" + Script + '\'' +
                    ", Interval='" + Interval + '\'' +
                    ", HTTP='" + HTTP + '\'' +
                    ", DeregisterCriticalServiceAfter='" + DeregisterCriticalServiceAfter + '\'' +
                    ", TTL='" + TTL + '\'' +
                    '}';
        }
    }
}
