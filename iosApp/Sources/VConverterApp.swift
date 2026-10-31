import SwiftUI
import UIKit

private func makeMainController() -> UIViewController {
    guard let type = NSClassFromString("VConverterMainKt") else { return UIViewController() }
    let selector = NSSelectorFromString("MainViewController")
    let result = (type as AnyObject).perform(selector)?.takeUnretainedValue()
    return (result as? UIViewController) ?? UIViewController()
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        makeMainController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct VConverterApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .preferredColorScheme(.dark)
        }
    }
}
