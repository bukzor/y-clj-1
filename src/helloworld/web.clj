(ns helloworld.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.middleware.params :as params]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.basic-authentication :as basic]
            [cemerick.drawbridge :as drawbridge]
            [environ.core :refer [env]]))

(defn- authenticated? [user pass]
  (= [user pass] [(env :repl-user false) (env :repl-password false)]))

(def ^:private drawbridge
  (-> (drawbridge/ring-handler)
      (session/wrap-session)
      (basic/wrap-basic-authentication authenticated?)))

(defroutes app
  (ANY "/repl" {:as req}
       (drawbridge req))
  (GET "/" []
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (pr-str ["Hello" :from 'Heroku])})
  (GET "/req" {:as req}
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (pr-str req)})
  ;; TODO Make the repetitive code a function.
  ;; TODO Create a route-to-reducer map, and use it to configure the app.
  (GET "/add" {:as req}
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (pr-str (reduce + (map #(Float/parseFloat (% 0)) (re-seq #"[+-]?\d+(\.\d+)?" (:query-string req)))))})
  (GET "/subtract" {:as req}
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (pr-str (reduce - (map #(Float/parseFloat (% 0)) (re-seq #"[+-]?\d+(\.\d+)?" (:query-string req)))))})
  (GET "/multiply" {:as req}
       {:status 200
        :headers {"Content-Type" "text/plain"}
        :body (pr-str (reduce * (map #(Float/parseFloat (% 0)) (re-seq #"[+-]?\d+(\.\d+)?" (:query-string req)))))})
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))
        store (cookie/cookie-store {:key (env :session-secret)})]
    (jetty/run-jetty (-> #'app
                         ((if (env :production)
                            wrap-error-page
                            trace/wrap-stacktrace))
                         params/wrap-params
                         (site {:session {:store store}}))
                     {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))
