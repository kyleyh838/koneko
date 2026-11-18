//go:build !with_openconnect

package libcore

import (
	"context"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/endpoint"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
)

// Mirrors sing-box's include/openconnect_stub.go: without the tag the
// type is still known at decode time, but creating the endpoint fails
// with a clear message instead of "unknown endpoint type".
func registerOpenConnectEndpoint(registry *endpoint.Registry) {
	endpoint.Register[option.OpenConnectEndpointOptions](registry, C.TypeOpenConnect, func(ctx context.Context, router adapter.Router, logger log.ContextLogger, tag string, options option.OpenConnectEndpointOptions) (adapter.Endpoint, error) {
		if !options.System {
			return nil, E.New(`OpenConnect is not included in this build, rebuild with -tags with_openconnect,with_gvisor for system:false`)
		}
		return nil, E.New(`OpenConnect is not included in this build, rebuild with -tags with_openconnect`)
	})
}
