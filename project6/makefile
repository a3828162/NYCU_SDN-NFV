all: create_container create_ovs_bridge set_link

create_container:
	sudo chmod 777 makefile
	docker compose up -d

create_ovs_bridge:
	sudo ovs-vsctl add-br OVS -- set Bridge OVS fail-mode=secure

set_link:
	sudo ovs-vsctl set-controller OVS tcp:127.0.0.1:6653
	sudo ovs-vsctl set bridge OVS protocol=OpenFlow14
	sudo ovs-docker add-port OVS eth3 R1 --ipaddress=172.20.0.2/16
	sudo ovs-docker add-port OVS eth3 R2 --ipaddress=172.20.0.3/16
	sudo ovs-docker add-port OVS eth3 R3 --ipaddress=172.20.0.4/16

clean:
	docker compose down
	sudo ovs-vsctl del-controller OVS
	sudo ovs-vsctl del-br OVS
	sudo mn -c