package pl.sviete.dom.connhist;

public class AisConnectionHistHolder {

        public String connUrl;
        public String connTime;
        public String gateID;

        public AisConnectionHistHolder(){

        }

        public AisConnectionHistHolder(String connUrl, String connTime, String gateID) {

            this.connUrl = connUrl;
            this.connTime = connTime;
            this.gateID = gateID;
        }
    }