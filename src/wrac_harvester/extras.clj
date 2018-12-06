(get-wetherby-runners
[runners]
(filter #(re-find #"(etherby)" (:club %)) runners))

(get-winner
[runners]
(filter #(re-find #"1" (:pos %)) runners))



