export const dynamic = 'force-dynamic';

export async function GET() {
  return Response.json({ status: 'UP', service: 'cvfacil-frontend', timestamp: Date.now() });
}
