(ns raven-clj.ring-test
  (:require [clojure.test :refer :all]
            [mocko.core :refer :all]
            [raven-clj.core :as raven]
            [raven-clj.ring :refer :all]))

(def e (Exception. "thing"))

(defn wrapped
  [req]
  (if (:ok req)
    "woo"
    (throw e)))

(def req
  {:scheme      :https
   :uri         "/hello-world"
   :method      :get
   :params      {:one 1}
   :headers     {"ok"   2
                 "host" "example.com"}
   :remote-addr "127.0.0.1"
   :session     {}})

(defn preprocess
  [req]
  (update req :params assoc :two 2))

(defn postprocess
  [req e]
  (assoc e :environment "qa"))

(defn error
  [req e]
  (assoc req :exception e))

(deftest wrap-report-exceptions-test
  (testing "passing through"
    (let [handler (wrap-report-exceptions wrapped "dsn" {})]
      (is (= "woo"
             (handler (assoc req :ok true))))))

  (testing "with defaults"
    (with-mocks
      (let [event   {:throwable e
                     :interfaces
                     {:request {:url          "https://example.com/hello-world"
                                :method       nil
                                :data         {:one 1}
                                :query_string ""
                                :headers      {"ok"   2
                                               "host" "example.com"}
                                :env          {:session      "{}"
                                               "REMOTE_ADDR" "127.0.0.1"}}
                      :user    {:ip_address "127.0.0.1"}}}
            handler (wrap-report-exceptions wrapped "dsn" {})]
        (mock! #'raven/send-event {["dsn" event] nil})
        (is (= {:status  500
                :headers {"Content-Type" "text/html"}
                :body    "<html><head><title>Error</title></head><body><p>Internal Server Error</p></body></html>"
                }
               (handler req))))))

  (testing "with callbacks"
    (with-mocks
      (let [event   {:throwable   e
                     :environment "qa"
                     :interfaces
                     {:request {:url          "https://example.com/hello-world"
                                :method       nil
                                :data         {:one 1
                                               :two 2}
                                :query_string ""
                                :headers      {"ok"   2
                                               "host" "example.com"}
                                :env          {:session      "{}"
                                               "REMOTE_ADDR" "127.0.0.1"}}
                      :user    {:ip_address "127.0.0.1"}}}
            handler (wrap-report-exceptions wrapped "dsn"
                                            {:preprocess-fn  preprocess
                                             :postprocess-fn postprocess
                                             :error-fn       error})]
        (mock! #'raven/send-event {["dsn" event] nil})
        (is (= (assoc req :exception e)
               (handler req)))))))
