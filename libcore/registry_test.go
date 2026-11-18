package libcore

import (
	"context"
	"os"
	"testing"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/service"
)

// Regression test for the four "unknown type" decode errors the phone
// reported on the 1.14.4 experimental build:
//
//	decode config: outbounds[0]: unknown outbound type: snell
//	decode config: outbounds[0]: unknown outbound type: naive
//	decode config: endpoints[0]: unknown endpoint type: openvpn-client
//	decode config: endpoints[0]: unknown endpoint type: openconnect
//
// Root cause: libcore does not use include.Context(). It builds its own
// registries by hand in box_include.go, and the four new protocols were
// never added to them. The build tags (with_naive_outbound,
// with_openvpn, with_openconnect) only compile the code in; the registry
// is what makes a type known at decode time. The About page lists the
// tags, so the build looked complete while decode still rejected them.
//
// This test decodes the same single-protocol configs the phone tests
// with, through the exact registries NewSingBoxInstance uses.
func TestNekoboxAndroidRegistriesKnowNewProtocols(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	ctx = box.Context(ctx,
		nekoboxAndroidInboundRegistry(), nekoboxAndroidOutboundRegistry(), nekoboxAndroidEndpointRegistry(),
		nekoboxAndroidDNSTransportRegistry(nil), nekoboxAndroidServiceRegistry(),
		nekoboxAndroidCertificateProviderRegistry(),
	)
	ctx = service.ContextWithDefaultRegistry(ctx)

	for _, name := range []string{"snell", "naive", "openvpn", "openconnect"} {
		raw, err := os.ReadFile("../../sing-box-audit/test-" + name + ".json")
		if err != nil {
			t.Fatalf("read test-%s.json: %v", name, err)
		}
		var options option.Options
		if err := options.UnmarshalJSONContext(ctx, raw); err != nil {
			t.Errorf("test-%s.json still fails to decode: %v", name, err)
		}
	}
}
