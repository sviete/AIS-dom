package pl.sviete.dom.connhist;

public class AisConnectionHistHolder {

        public String connUrl;
        public String connTime;
        public String gateID;
        public String localIP;

        public AisConnectionHistHolder(){

        }

        public AisConnectionHistHolder(String connUrl, String connTime, String gateID, String localIP) {

            this.connUrl = connUrl;
            this.connTime = connTime;
            this.gateID = gateID;
            this.localIP = localIP;
        }
    }