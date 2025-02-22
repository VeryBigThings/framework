(ns framework.route.helpers
  "The default not found and action functions"
  (:require
    [xiana.core :as xiana]))

(defn not-found
  "Default not-found response handler helper."
  [state]
  (xiana/error
    (-> state
        (assoc :response {:status 404 :body "Not Found"}))))

(defn action
  "Default action response handler helper."
  [{request :request {handler :handler} :request-data :as state}]
  (try
    (xiana/ok
      (assoc state :response (handler request)))
    (catch Exception e
      (xiana/error
        (-> state
            (assoc :error e)
            (assoc :response
                   {:status 500 :body "Internal Server error"}))))))
