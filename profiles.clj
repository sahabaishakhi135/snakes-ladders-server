{:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                      [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
                      [org.clojure/tools.trace "0.7.10"]
                      [ring/ring-devel "1.7.0-RC1"]
                      [ring/ring-mock "0.3.2"]
                      [peridot "0.5.1"]]
       :env {:api-key "bb4b5b1c-b922-48e9-8220-3027a2ad0b6f"
             :secret-key "41abe20f-8ad9-4f1b-bedc-742556f0890c"
             :templates-root "public"
             :allow-cors-sessions "true"
             :mysql-user "root"
             :mysql-pass "sonyP910i"
             :director-service "http://localhost:9091"
             :turn-timeout 15
             ;:kafka-url "localhost:9092"
             :port 8090
             :host-id 1337
             ;:tables-json "http://localhost:9091/tables"
             :tables-json "tables.json"
             :host "127.0.0.1:8090"
             :config-src "edn"}
       :main snakesladders.web}
 :test {:dependencies [[clj-http "3.10.1"]]
        :env {:templates-root "public"
              :tables-json "tables.json"
              :config-src "edn"}}
 :staging {:env {:templates-root "/var/www/templates"}}
 :production {:env {:templates-root "/var/www/templates"}}
 :uberjar {:omit-source true
           :main snakesladders.web
           :aot :all}}
