import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Navbar } from '@/components/Navbar';
import { useAuthStore } from '@/stores/authStore'; // <--- Import Auth Store
import {
  FileCode,
  Zap,
  GitBranch,
  Brain,
  ArrowRight,
  Sparkles,
  FileText,
  LayoutDashboard,
  ChevronDown
} from 'lucide-react';

export default function Index() {
  // 1. Get the token to check if user is logged in
  const { token } = useAuthStore();

  return (
    <div className="min-h-screen overflow-x-hidden">
      <Navbar />

      {/* Hero Section */}
      <section className="relative min-h-screen flex items-center justify-center overflow-hidden">
        {/* Background Effects */}
        <div className="absolute inset-0 gradient-mesh" />
        <div className="absolute inset-0 overflow-hidden pointer-events-none">
          <div className="absolute top-1/4 left-1/4 w-[250px] sm:w-[400px] lg:w-[500px] h-[250px] sm:h-[400px] lg:h-[500px] bg-accent/10 rounded-full blur-[120px] animate-float" />
          <div className="absolute bottom-1/3 right-1/4 w-[200px] sm:w-[300px] lg:w-[400px] h-[200px] sm:h-[300px] lg:h-[400px] bg-primary/10 rounded-full blur-[100px] animate-float" style={{ animationDelay: '2s' }} />
        </div>

        {/* Grid overlay */}
        <div className="absolute inset-0 bg-[linear-gradient(rgba(59,130,246,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(59,130,246,0.03)_1px,transparent_1px)] bg-[size:60px_60px]" />

        <div className="relative z-10 container mx-auto px-5 sm:px-6 text-center">
          <div className="max-w-4xl mx-auto">
            {/* Badge */}
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full glass mb-8 animate-fade-in">
              <Sparkles className="h-4 w-4 text-accent" />
              <span className="text-sm text-muted-foreground">AI-Powered Documentation</span>
            </div>

            {/* Main Heading */}
            <h1 className="text-3xl sm:text-5xl lg:text-7xl font-bold mb-6 animate-fade-in" style={{ animationDelay: '100ms' }}>
              <span className="text-foreground">Transform Code into</span>
              <br />
              <span className="text-gradient">Crystal-Clear Docs</span>
            </h1>

            {/* Subtitle */}
            <p className="text-base sm:text-xl text-muted-foreground mb-8 sm:mb-10 max-w-2xl mx-auto animate-fade-in" style={{ animationDelay: '200ms' }}>
              Intelli-Doc AI analyzes your repositories and generates comprehensive,
              professional documentation using advanced AI technology.
            </p>

            {/* CTA Buttons - 🔥 DYNAMIC LOGIC HERE */}
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 animate-fade-in" style={{ animationDelay: '300ms' }}>

              {/* If Logged In -> Go Dashboard. If Logged Out -> Go Auth */}
              <Link to={token ? "/dashboard" : "/auth"}>
                <Button variant="neon" size="xl" className="group">
                  {token ? (
                    <>
                      Go to Dashboard
                      <LayoutDashboard className="h-5 w-5 ml-2 transition-transform group-hover:scale-110" />
                    </>
                  ) : (
                    <>
                      Get Started Free
                      <ArrowRight className="h-5 w-5 ml-2 transition-transform group-hover:translate-x-1" />
                    </>
                  )}
                </Button>
              </Link>

              {/* Learn More Button - Scrolls to Features */}
              <Button
                variant="glass"
                size="xl"
                onClick={() => {
                  document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' });
                }}
                className="group"
              >
                Learn More
                <ChevronDown className="h-5 w-5 ml-2 transition-transform group-hover:translate-y-1" />
              </Button>
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section id="features" className="py-12 sm:py-24 relative scroll-mt-16">
        <div className="container mx-auto px-5 sm:px-6">
          <div className="text-center mb-16">
            <h2 className="text-2xl sm:text-3xl lg:text-4xl font-bold text-foreground mb-4">
              Intelligent Documentation Pipeline
            </h2>
            <p className="text-muted-foreground max-w-2xl mx-auto">
              Our AI-powered system understands your code structure and generates
              documentation that actually makes sense.
            </p>
          </div>

          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-6">
            {[
              {
                icon: GitBranch,
                title: 'Connect Repository',
                description: 'Simply paste your GitHub URL and our system clones and analyzes your codebase.',
              },
              {
                icon: Brain,
                title: 'AI Analysis',
                description: 'Our Architect Agent identifies the most critical files for documentation.',
              },
              {
                icon: FileText,
                title: 'Generate Docs',
                description: 'Get beautifully formatted README and documentation in seconds.',
              },
            ].map((feature, index) => (
              <div
                key={feature.title}
                className="glass rounded-2xl p-6 sm:p-8 group hover:neon-glow-sm transition-all duration-500 animate-fade-in"
                style={{ animationDelay: `${index * 150}ms` }}
              >
                <div className="relative inline-flex items-center justify-center mb-6">
                  <feature.icon className="h-10 w-10 text-accent transition-transform group-hover:scale-110" />
                  <div className="absolute inset-0 blur-xl bg-accent/20 opacity-0 group-hover:opacity-100 transition-opacity" />
                </div>
                <h3 className="text-xl font-semibold text-foreground mb-3">{feature.title}</h3>
                <p className="text-muted-foreground">{feature.description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How It Works Section - Kept same as original */}
      <section className="py-12 sm:py-24 relative">
        <div className="container mx-auto px-5 sm:px-6">
          <div className="glass rounded-2xl sm:rounded-3xl p-5 sm:p-8 md:p-12 neon-border">
            <div className="grid md:grid-cols-2 gap-12 items-center">
              <div>
                <h2 className="text-2xl sm:text-3xl lg:text-4xl font-bold text-foreground mb-4 sm:mb-6">
                  Built for Developers,
                  <br />
                  <span className="text-gradient">By Developers</span>
                </h2>
                <p className="text-muted-foreground mb-8">
                  Stop wasting hours writing documentation. Let AI handle the heavy lifting
                  while you focus on building amazing software.
                </p>
                <div className="space-y-4">
                  {[
                    'Automatic code structure analysis',
                    'Smart file prioritization',
                    'Markdown-formatted output',
                    'Real-time progress tracking',
                  ].map((item) => (
                    <div key={item} className="flex items-center gap-3">
                      <div className="h-2 w-2 rounded-full bg-accent" />
                      <span className="text-foreground">{item}</span>
                    </div>
                  ))}
                </div>
              </div>
              <div className="relative">
                <div className="glass rounded-2xl p-4 sm:p-6 font-mono text-xs sm:text-sm">
                  <div className="flex items-center gap-2 mb-4">
                    <div className="h-3 w-3 rounded-full bg-destructive" />
                    <div className="h-3 w-3 rounded-full bg-yellow-500" />
                    <div className="h-3 w-3 rounded-full bg-green-500" />
                  </div>
                  <pre className="text-muted-foreground overflow-x-auto">
                    <code>{`{
  "status": "ANALYZING_CODE",
  "repository": "user/awesome-project",
  "files_analyzed": 47,
  "ai_agent": "active"
}`}</code>
                  </pre>
                </div>
                <div className="absolute -bottom-4 -right-4 h-full w-full glass rounded-2xl -z-10 opacity-50" />
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Footer CTA - 🔥 DYNAMIC LOGIC HERE TOO */}
      <section className="py-12 sm:py-24 relative">
        <div className="container mx-auto px-5 sm:px-6 text-center">
          <div className="relative inline-flex items-center justify-center mb-6">
            <FileCode className="h-16 w-16 text-accent" />
            <div className="absolute inset-0 blur-2xl bg-accent/30 animate-pulse-neon" />
          </div>
          <h2 className="text-2xl sm:text-3xl lg:text-4xl font-bold text-foreground mb-4">
            Ready to Automate Your Documentation?
          </h2>
          <p className="text-muted-foreground mb-8 max-w-xl mx-auto">
            Join developers who are saving hours every week with AI-powered documentation.
          </p>
          <Link to={token ? "/dashboard" : "/auth"}>
            <Button variant="neon" size="xl">
              {token ? "Go to Dashboard" : "Start Documenting Now"}
              <Zap className="h-5 w-5 ml-2" />
            </Button>
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="py-8 border-t border-border">
        <div className="container mx-auto px-4 text-center text-sm text-muted-foreground">
          <p>© 2026 Intelli-Doc AI. Built with ❤️ for developers.</p>
        </div>
      </footer>
    </div>
  );
}