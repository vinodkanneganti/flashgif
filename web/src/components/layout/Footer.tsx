export function Footer() {
  return (
    <footer className="border-t mt-16">
      <div className="container mx-auto px-4 py-6 text-sm text-muted-foreground flex flex-col sm:flex-row gap-2 sm:justify-between">
        <span>© FlashGif</span>
        <span>
          <a
            href="http://localhost:8080/swagger-ui.html"
            className="hover:underline"
            target="_blank"
            rel="noopener noreferrer"
          >
            API docs
          </a>
        </span>
      </div>
    </footer>
  );
}
