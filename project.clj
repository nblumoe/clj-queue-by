(defproject com.acrolinx/clj-queue-by "0.1.0-SNAPSHOT"
  :description "A queue which schedules by key."
  :url "http://github.com/acrolinx/clj-queue-by"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/"}
  :deploy-repositories [["releases"
                         {:url "https://repo.clojars.org/"
                          :password :env/CLOJARS_PASS
                          :username :env/CLOJARS_USER}]]
  :dependencies [[org.clojure/clojure "1.8.0"]])
