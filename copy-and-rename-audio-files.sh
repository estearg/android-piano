#!/bin/bash

n=0

for i in 3 4 5
do
	for j in C Db D Eb E F Gb G Ab A Bb B
	do
		cp "Converted audio files"/Piano.mf.$j$i.ogg res/raw/note$n.ogg
		let n=$n+1
	done
done
