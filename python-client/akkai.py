#!/usr/bin/env python3

from __future__ import print_function

import logging
import argparse

import grpc

import inspection_pb2
import inspection_pb2_grpc

def inspectable(stub):
    """Retrieve and print out the list of inspectable actors."""
    response = stub.RequestInspectableActors(inspection_pb2.InspectableActorsRequest())
    for actor in sorted(response.inspectable_actors):
        print(actor)

def fragments(stub, actor, ids, no_state):
    """Retrieve and print out the fragments with the ids of the given actor."""
    response = stub.RequestFragments(inspection_pb2.FragmentsRequest(actor=actor, fragment_ids=ids))

    if response.WhichOneof('res') == 'error':
        # data = getattr(response.error, response.error.WhichOneof('error'))
        print(response.error)
    else:
        if not no_state: 
            print('State: %s\n' % response.fragments.state)    
        for key in sorted(response.fragments.fragments):
            print("%s = %s" % (key, response.fragments.fragments[key]))

def in_group(stub, group):
    """Retrieve and print out the inpsectable actors in the given group."""
    response = stub.RequestGroup(inspection_pb2.GroupRequest(group=group))
    for actor in sorted(response.inspectable_actors):
        print(actor)
    
def run():
    parser = argparse.ArgumentParser(
        description='Akka-Inspection',
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument('-H', '--host', default='127.0.0.1', help="The hostname to connect to.")
    parser.add_argument('-p', '--port', default='8080', help='The port to connect to.')
    parser.add_argument('-ns', '--no-state', action='store_true', default=False)

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-l', '--ls', action='store_true', default=None, help='List all the inspectable actors.')
    group.add_argument('-ig', '--in-group', help='List all the inspectable actors in the group.')
    group.add_argument('-i', '--inspect', nargs='+', help='Inspect an actor. \nTakes the actor\'s address followed by the fragment-ids. \nIf no ids are provided returns all the valid ids.')

    args = parser.parse_args()

    with grpc.insecure_channel(args.host + ':' + args.port) as channel:
        stub = inspection_pb2_grpc.ActorInspectionServiceStub(channel)
        
        if args.ls: inspectable(stub)
        elif args.inspect is not None:
            no_state = args.no_state
            if len(args.inspect) == 1: fragments(stub, args.inspect[0], '*', no_state) # retrieve all the fragments
            elif len(args.inspect) > 1: fragments(stub, args.inspect[0], args.inspect[1:], no_state) # retrive the specified fragments
        else: in_group(stub, args.in_group)        

if __name__ == '__main__':
    logging.basicConfig()
    run()
