package br.agr.terras.corelibrary.infraestructure.resources.geo;

/**
 * Point on 2D landscape
 * 
 * @author Roman Kushnarenko (sromku@gmail.com)</br>
 */
public class PointFunctions
{
	public PointFunctions(double x, double y)
	{
		this.x = x;
		this.y = y;
	}

	public double x;
	public double y;

	@Override
	public String toString()
	{
		return String.format("(%.2f,%.2f)", x, y);
	}
}