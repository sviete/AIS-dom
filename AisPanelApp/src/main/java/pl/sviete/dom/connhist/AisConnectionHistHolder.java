package pl.sviete.dom.connhist;

public class AisConnectionHistHolder {

        public String connUrl;
        public String connTime;
        public String gateID;
        public String localIP;
        public String connUser;
        public String connDesc;

        public AisConnectionHistHolder(){

        }

        public AisConnectionHistHolder(String connUrl, String connTime, String gateID,
                                       String localIP, String connUser, String connDesc) {

            this.connUrl = connUrl;
            this.connTime = connTime;
            this.gateID = gateID;
            this.localIP = localIP;
            this.connUser = connUser;
            this.connDesc = connDesc;
        }
    }