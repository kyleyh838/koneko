package libcore

import (
	"fmt"
	"net"
	"testing"
)

// doH3Direct must never return (nil, nil): when both direct attempts hang
// and the 10s context times out with no recorded error, the caller
// (RawUpdater) gets a null response with no error and NPEs on
// response.contentString.
//
// A silent local listener makes the ECH attempt hang deterministically:
// the TCP dial succeeds, then the TLS handshake blocks forever because
// the server never answers, and nothing ever records an error before
// the 10s timeout fires. Scheme http keeps only the ECH attempt.
func TestDoH3DirectTimeoutReturnsError(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			// Hold the connection open and never answer: the TLS
			// handshake on the other end blocks until the timeout.
			_ = conn
		}
	}()

	req := &httpRequest{httpClient: &httpClient{}}
	if err := req.SetURL(fmt.Sprintf("http://%s/sub", listener.Addr())); err != nil {
		t.Fatal(err)
	}
	req.tryH3Direct = true
	resp, err := req.doH3Direct()
	if resp != nil {
		t.Fatalf("expected nil response, got %v", resp)
	}
	if err == nil {
		t.Fatal("expected non-nil error, got (nil, nil)")
	}
}
