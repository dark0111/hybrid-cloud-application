curl -X PUT http://211.109.179.19:5984/_users
curl -X PUT http://211.109.179.19:5984/airlines
curl -X PUT http://211.109.179.19:5984/bookings
curl -X PUT http://211.109.179.19:5984/airlines/"001" -d '{ "name" : "American Airline" , "contactPhone": "1-888-1234-567" }'
curl -X PUT http://211.109.179.19:5984/airlines/"002" -d '{ "name" : "United Airline" , "contactPhone": "1-855-1284-563" }'
curl -X PUT http://211.109.179.19:5984/airlines/"003" -d '{ "name" : "Delta" , "contactPhone": "1-855-4254-000" }'