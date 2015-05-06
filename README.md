# software-defined-router
BUG:
1.Change cost need a particular signal.

TODO:
√finish file transfer
1.add signal for change cost
2.add filename in header
3.normalize the serializeService method
4.bonus



Transmission protocol

The packet is designed as follow:

| message type | source IP    | source port |    cost    | destination IP | destination port | distance vector/BinFile |
|   short: 2B  | String: 15B  |   int: 4B   | float: 4B  |  String: 15B   |   int: 4B        |     ........            |


The distance vector mentioned above is stored as follow: (total 46*n B)
| destination1(IP:port) |   cost1   | firstHop1(IP:port) | destination2(IP:port) |   cost2   | firstHop2(IP:port) | ...
|     String: 21B       | float: 4B |     String: 21B    |     String: 21B       | float: 4B |     String: 21B    | ...

The BinFile format
|file name|offset|checksum|Bin|
|   26 B  |  4B  |short:2B|...|

Here are more details of how each field is used:

1. message type: We use this number to distinguish different type of message.If type=1, it means it is a ROUTE
    UPDATE/CHAGNECOST message. In addition, 2 stands for LINKDOWN message, 3 stands for LINKUP message and 4 stands for
    NEWCOST message(for additional feature).

2. source port: Since the receiver can get the source IP from the packet, we need to add the source port number
    of the source so that the receiver can identify the sender.

3. cost: It is useful if a new node is added and it will send update message to its neighbors. Then the neighbors
    can know the cost of the path between them. And it is used in one of the additional features I implemented. In
    that feature, we can update the cost of the edge and send the information to the corresponding neighbor.

4: destination IP: Since an IP has at most 15 characters including the dot, we can present it using a string of
    length 15 (spaces are left if we use less than 15 characters).

5. distance vector: Since the IP address(V4) cannot be longer than 15 characters and port number cannot be longer
    than 5 characters, we can use 21 bytes of String to store an address in the following format: "IP:port", for
    example, 127.0.0.1:4116. Then each element in the distance vector can be represented by 46 bytes, including 21
    bytes of destination address (IP:port), 4 bytes of cost, 21 bytes of first hop address(IP:port).
