(ns components-test
  (:require
    [clj-http.client :as http]
    [clojure.test :refer [deftest is use-fixtures]]
    [com.stuartsierra.component :as component]
    [components :as comps]
    [framework.config.core :as config]))

(defn std-system-fixture
  [f]
  (let [config (config/edn)
        system (-> config
                   comps/system
                   component/start)]
    (try
      (f)
      (finally
        (component/stop system)))))

(use-fixtures :each std-system-fixture)

(deftest testing-controllers
  (is (= {:body   "Unauthorized"
          :status 401}
         (-> {:url                  "http://localhost:3000/"
              :unexceptional-status (constantly true)
              :method               :get}
             http/request
             (select-keys [:status :body]))))
  (is (= {:body   "Unauthorized"
          :status 401}
         (-> {:url                  "http://localhost:3000/"
              :unexceptional-status (constantly true)
              :method               :get}
             http/request
             (select-keys [:status :body]))))
  (is (= {:body   "Index page"
          :status 200}
         (-> {:url                  "http://localhost:3000/"
              :unexceptional-status (constantly true)
              :basic-auth           ["aladdin" "opensesame"]
              :method               :get}
             http/request
             (select-keys [:status :body]))))
  (is (= {:body   "Not Found"
          :status 404}
         (-> {:url                  "http://localhost:3000/wrong"
              :unexceptional-status (constantly true)
              :basic-auth           ["aladdin" "opensesame"]
              :method               :get}
             http/request
             (select-keys [:status :body])))))

(deftest testing-content-negotiation
  (is (= {:body   "<tag>trebuchet</tag>"
          :status 200}
         (-> {:url                  "http://localhost:3000/api/siege-machines/1"
              :unexceptional-status (constantly true)
              :basic-auth           ["aladdin" "opensesame"]
              ;:headers              {"Accept" "application/xml"}
              :accept               :application/xml
              :method               :get}
             http/request
             (select-keys [:status :body]))))
  (is (= {:body   "\"trebuchet\""
          :status 200}
         (-> {:url                  "http://localhost:3000/api/siege-machines/1"
              :unexceptional-status (constantly true)
              :basic-auth           ["aladdin" "opensesame"]
              :accept               :application/json
              :method               :get}
             http/request
             (select-keys [:status :body]))))
  ;TODO here I would like to have 400 and some reasonable explanation
  (is (= {:body   "Internal Server error"
          :status 500}
         (-> {:url                  "http://localhost:3000/api/siege-machines/1c"
              :unexceptional-status (constantly true)
              :basic-auth           ["aladdin" "opensesame"]
              :accept               :application/json
              :method               :get}
             http/request
             (select-keys [:status :body]))))

  (is (= {:body   "Internal Server error"
          :status 500}
         (-> {:url                  "http://localhost:3000/api/siege-machines/3"
              :unexceptional-status (constantly true)
              :basic-auth           ["aladdin" "opensesame"]
              :accept               :application/json
              :method               :get}
             http/request
             (select-keys [:status :body])))))