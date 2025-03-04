#!/bin/bash

# Kill dead pane: Ctrl-b x
# Kill active session: Ctrl-b &

path="os/project1"

ssh -t dc01 "builtin cd $path; make" && tmux \
    new-session  -n osp1 "ssh -t dc01 'export TERM=xterm; builtin cd $path && make 0; bash'" \; \
    split-window -t 0 -h "ssh -t dc02 'export TERM=xterm; builtin cd $path && make 1; bash'" \; \
    split-window -t 0 -v "ssh -t dc03 'export TERM=xterm; builtin cd $path && make 2; bash'" \; \
    split-window -t 2 -v "ssh -t dc04 'export TERM=xterm; builtin cd $path && make 3; bash'" \; \
    select-pane  -t 0;

#ssh -t dc01 "builtin cd $path; make" && tmux \
#    new-session  -n osp1 "ssh -t dc01 'export TERM=xterm; builtin cd $path && make 0 > node1.out; bash'" \; \
#    split-window -t 0 -h "ssh -t dc02 'export TERM=xterm; builtin cd $path && make 1 > node2.out; bash'" \; \
#    split-window -t 0 -v "ssh -t dc03 'export TERM=xterm; builtin cd $path && make 2 > node3.out; bash'" \; \
#    split-window -t 2 -v "ssh -t dc04 'export TERM=xterm; builtin cd $path && make 3 > node4.out; bash'" \; \
#    select-pane  -t 0;
