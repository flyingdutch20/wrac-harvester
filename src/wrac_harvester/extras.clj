(get-wetherby-runners
[runners]
(filter #(re-find #"(etherby)" (:club %)) runners))

(get-winner
[runners]
(filter #(re-find #"1" (:pos %)) runners))

;Find first male/female
;what if there are more than one races.
;first count the number of :pos "1". If only 1 then find find first male and first female
;(for [runner runners :while (= (:sex runner) "M"))
