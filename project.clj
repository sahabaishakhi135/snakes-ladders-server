(defproject snakesladders "1.0.0"
  :description "snakesladders game server by VUME.IN, in Clojure"
  :url "http://www.pekaplay.com/"
  :main snakesladders.web
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/data.zip "0.1.2"]
                 [better-cond "2.1.4"]
                 [camel-snake-kebab "0.4.0"]
                 [nrepl "0.8.3"]

                 ; Order of these 2 libraries is important. encore version issue
                 [com.taoensso/sente "1.16.0-alpha1"]
                 [com.taoensso/carmine "2.19.1"]

                 [fundingcircle/jackdaw "0.7.6"]

                 [clj-http "3.12.3"]
                 [cheshire "5.10.0"]

                 [cheshire "5.8.0"]
                 [clj-time "0.14.4"]
                 [danlentz/clj-uuid "0.1.7"]
                 [clj-http "3.10.0"]
                 [environ "1.1.0"]
                 [buddy "2.0.0"]
                 [metosin/malli "0.2.1"]

                 ; logging
                 [org.clojure/tools.logging "0.4.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [ch.qos.logback/logback-core "1.1.2"]
                 [org.slf4j/slf4j-api "1.7.9"]
                 [org.codehaus.janino/janino "2.6.1"]
                 [ring-logger "1.0.1"]

                 ; db
                 [org.clojure/java.jdbc "0.7.8"]
                 [com.mchange/c3p0 "0.9.5.2"]

                 ; web server
                 [http-kit "2.3.0"]
                 [ring/ring-spec "0.0.4"]
                 [ring/ring-core "1.9.5" :exclusions [ring/ring-codec]]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.4.0"]
                 [compojure "1.6.1"]

                 [pekaplay-base "0.6.8"]]

  :test-paths ["test" "e2e"]

  :test-selectors {:default (complement :integration)
                   :integration :integration}

  :min-lein-version "2.5.0"

  :plugins [[lein-environ   "1.1.0"]
;            [lein-cljsbuild "1.1.7"]
            [migratus-lein  "0.7.2"]]

;  :cljsbuild
;  {:builds
;   [{:id :cljs-client
;     :source-paths ["src"]
;     :compiler {:output-to "resources/public/comms.js"
;                :optimizations :whitespace #_:advanced
;                :pretty-print true}}]}
;
;  :clean-targets ^{:protect false} ["resources/public/comms.js"]

  :jvm-opts ["-Xmx32g"
             "-XX:+IgnoreUnrecognizedVMOptions"
             "-XX:+UseParNewGC"
             "-XX:+CMSParallelRemarkEnabled"
             "-XX:+UseFastAccessorMethods"
             "-server"
             "-XX:-OmitStackTraceInFastThrow"]
  :migratus {:store :database
             :migration-dir ~(or (System/getenv "MIGRATIONS_TABLE") "migrations")
             :db {:classname "com.mysql.jdbc.Driver"
                  :subprotocol "mysql"
                  :subname ~(str "//localhost:3306/" (or (System/getenv "MYSQL_DB") "pekaplay"))
                  :user ~(or (System/getenv "MYSQL_USER") "root")
                  :password ~(or (System/getenv "MYSQL_PASS") "sonyP910i")}})
