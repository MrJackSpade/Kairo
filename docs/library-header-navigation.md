# Library header controller navigation

Up from the first game (or an empty result list) enters the header. Left/Right selects Menu or Search, confirm opens it, and Down or Back returns to the game list. Closing the menu restores its header focus. Up from the search field returns to the Search button; Down returns to the results. Horizontal cursor movement remains native Android text editing.

The shared CatalogProgressFixture exercises actual Activity key and joystick-hat dispatch in both products: reaching and opening Menu/Search, returning from the drawer and search field, moving across the header by hat, and returning to the first game with Down or Back. Both debug app and instrumentation builds passed on RGDS. The existing catalog-lock input responsiveness regression also passed. Retroid was unavailable at its fixed endpoint and was not advertised by mDNS.

Game details also use shared explicit key/hat traversal. Up/Left moves backward and Down/Right forward through Library, the available screenshot, Play and Game settings, skipping disabled actions. The initial action accepts focus in touch mode, and focused controls are scrolled into view. Down beyond the last action scrolls the description.

Both products passed the details-page key/hat, settings-return and off-screen-focus regression on RGDS. Thumb overhang also passed a rendered-pixel assertion in both apps.
