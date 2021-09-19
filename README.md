# Replicated Key-Value Storage
#### A naive DynamoDB implementation.
#### This project is a part of the Distributed Systems Course taken by [Ethan Blanton](https://cse.buffalo.edu/~eblanton/) at the University at Buffalo.

---

### Functional Requirements

* Storage should support insert, delete and query.
* Storage should support 


### Non Functional Requirements

* Implementation provides strong consistency guranatees by leveraging Lineariability. 
* Implementation maintains high availabilty.
* Handle temporary single node failures. Upon recovery, the recovered node updates itself with the objects missed during the down time.
* Data replication should happen over three consecutive partitions/nodes.


### Project Guidelines

The following is a guideline for your content provider based on the design of Amazon Dynamo: 
##### Membership 
 * Just as the original Dynamo, every node can know every other node.​ This means 
that each node knows all other nodes in the system and also knows exactly 
which partition belongs to which node; any node can forward a request to the 
correct node without using a ring-based routing. 
##### Request routing 
 * Unlike Chord, each Dynamo node knows all other nodes in the system and also 
knows exactly which partition belongs to which node. 
 * Under no failures, a request for a key is directly forwarded to the coordinator (i.e., 
the successor of the key), and the coordinator should be in charge of serving 
read/write operations. 
##### Quorum replication 
 * For linearizability, you can implement a quorum-based replication used by 
Dynamo. 
 * Note that the original design does not provide linearizability. You need to adapt 
the design. 
 * The replication degree N should be 3.​ This means that given a key, the key’s 
coordinator as well as the 2 successor nodes in the Dynamo ring should store the 
key. 
 * Both the reader quorum size R and the writer quorum size W should be 2. 
 * The coordinator for a get/put request should ​always contact other two nodes​ and 
get a vote from each (i.e., an acknowledgement for a write, or a value for a read). 
 * For write operations, all objects can be ​versioned​ in order to distinguish stale 
copies from the most recent copy. 
 * For read operations, if the readers in the reader quorum have different versions 
of the same object, the coordinator should pick the most recent version and 
return it. 
##### Chain replication 
 * Another replication strategy you can implement is chain replication, which 
provides linearizability. 
 * If you are interested in more details, please take a look at the following paper: 
http://www.cs.cornell.edu/home/rvr/papers/osdi04.pdf 
 * In chain replication, a write operation always comes to the first partition; then it 
propagates to the next two partitions in sequence. The last partition returns the 
result of the write. 
 * A read operation always comes to the last partition and reads the value from the 
last partition. 
##### Failure handling 
 * Handling failures should be done very carefully because there can be many 
corner cases to consider and cover. 
 * Just as the original Dynamo, each request can be used to detect a node failure. 
 * For this purpose, you can use a timeout for a socket read;​ you can pick a 
reasonable timeout value, e.g., 100 ms, and if a node does not respond within 
the timeout, you can consider it a failure. 
 * Do not rely on socket creation or connect status to determine if a node has 
failed.​ Due to the Android emulator networking setup, it is ​not​ safe to rely on 
socket creation or connect status to judge node failures. Please use an explicit 
method to test whether an app instance is running or not, e.g., using a socket 
read timeout as described above. 
*  When a coordinator for a request fails and it does not respond to the request, ​its 
successor can be contacted next for the request.
